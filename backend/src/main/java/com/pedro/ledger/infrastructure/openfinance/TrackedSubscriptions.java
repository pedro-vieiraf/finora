package com.pedro.ledger.infrastructure.openfinance;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TrackedSubscriptions {

  private static final ZoneId DISPLAY_ZONE = ZoneId.of("America/Sao_Paulo");
  private final JdbcTemplate jdbc;
  private final ConnectedCardBills cardBills;
  private final Clock clock;

  public TrackedSubscriptions(JdbcTemplate jdbc, ConnectedCardBills cardBills, Clock clock) {
    this.jdbc = jdbc;
    this.cardBills = cardBills;
    this.clock = clock;
  }

  public void trackBank(UUID transactionId) {
    var candidates = jdbc.query("""
        SELECT t.id, t.account_id, t.description, t.amount, t.currency, t.timestamp, a.name
        FROM transactions t JOIN accounts a ON a.id = t.account_id
        WHERE t.id = ? AND t.source = 'OPEN_FINANCE' AND t.type = 'EXPENSE' AND t.status = 'ACTIVE'
        """, (row, index) -> new Charge(row.getString("id"), row.getObject("account_id", UUID.class),
        row.getString("description"), row.getBigDecimal("amount"), row.getString("currency"),
        row.getTimestamp("timestamp").toInstant(), row.getString("name")), transactionId);
    track("BANK", candidates.stream().findFirst().orElseThrow(TrackedSubscriptions::missingCharge));
  }

  public void trackCard(UUID cardId, String chargeId) {
    var card = cardBills.findAll().stream().filter(item -> item.id().equals(cardId)).findFirst()
        .orElseThrow(TrackedSubscriptions::missingCharge);
    track("CARD", cardCharges(cardBills.confirmedCharges(), cardId, card.bankName()).stream()
        .filter(charge -> charge.id().equals(chargeId)).findFirst()
        .orElseThrow(TrackedSubscriptions::missingCharge));
  }

  public void remove(UUID id) {
    jdbc.update("DELETE FROM open_finance_tracked_subscriptions WHERE id = ?", id);
  }

  public List<Subscription> findAll() {
    var tracked = jdbc.query("""
        SELECT id, source, account_ref, description, merchant_key, currency, anchor_day,
               initial_amount, initial_date, account_label
        FROM open_finance_tracked_subscriptions ORDER BY description, id
        """, (row, index) -> new Tracked(row.getObject("id", UUID.class),
        row.getString("source"), row.getObject("account_ref", UUID.class), row.getString("description"),
        row.getString("merchant_key"), row.getString("currency"), row.getInt("anchor_day"),
        row.getBigDecimal("initial_amount"), row.getObject("initial_date", LocalDate.class),
        row.getString("account_label")));
    Map<UUID, List<ConnectedCardBills.Entry>> cardData = Map.of();
    if (tracked.stream().anyMatch(item -> "CARD".equals(item.source()))) {
      try {
        cardData = cardBills.confirmedCharges();
      } catch (RuntimeException exception) {
        // Keep the original estimate when the provider is unavailable.
      }
    }
    var today = clock.instant().atZone(DISPLAY_ZONE).toLocalDate();
    var loadedCharges = cardData;
    return tracked.stream().map(item -> {
      boolean bank = "BANK".equals(item.source());
      var charges = bank ? bankCharges(item)
          : cardCharges(loadedCharges, item.accountRef(), item.accountLabel());
      boolean available = bank || loadedCharges.containsKey(item.accountRef());
      return summarize(item, charges, today, available);
    }).toList();
  }

  private List<Charge> bankCharges(Tracked item) {
    return jdbc.query("""
        SELECT t.id, t.description, t.amount, t.currency, t.timestamp, a.name
        FROM transactions t JOIN accounts a ON a.id = t.account_id
        WHERE t.account_id = ? AND t.source = 'OPEN_FINANCE' AND t.type = 'EXPENSE'
          AND t.status = 'ACTIVE' AND t.currency = ? AND t.timestamp >= ?
        """, (row, index) -> new Charge(row.getString("id"), item.accountRef(),
        row.getString("description"), row.getBigDecimal("amount"), row.getString("currency"),
        row.getTimestamp("timestamp").toInstant(), row.getString("name")),
        item.accountRef(), item.currency(),
        java.sql.Timestamp.from(item.initialDate().atStartOfDay(DISPLAY_ZONE).toInstant()));
  }

  private static List<Charge> cardCharges(
      Map<UUID, List<ConnectedCardBills.Entry>> entries, UUID cardId, String accountLabel
  ) {
    return entries.getOrDefault(cardId, List.of()).stream()
        .map(entry -> new Charge(entry.id(), cardId, entry.description(), entry.amount(),
            entry.currency(), entry.date(), accountLabel))
        .toList();
  }

  private void track(String source, Charge charge) {
    if (charge.description() == null || charge.description().isBlank() || charge.currency() == null) {
      throw missingCharge();
    }
    var date = dateOf(charge);
    jdbc.update("""
        INSERT INTO open_finance_tracked_subscriptions
          (id, source, account_ref, description, merchant_key, currency, anchor_day,
           initial_amount, initial_date, account_label)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (source, account_ref, merchant_key, currency) DO NOTHING
        """, UUID.randomUUID(), source, charge.accountRef(), charge.description(), merchantKey(charge.description()),
        charge.currency(), date.getDayOfMonth(), charge.amount(), date, charge.accountLabel());
  }

  static Subscription summarize(Tracked tracked, List<Charge> charges, LocalDate today, boolean available) {
    var last = charges.stream()
        .filter(charge -> tracked.accountRef().equals(charge.accountRef())
            && tracked.currency().equals(charge.currency())
            && tracked.merchantKey().equals(merchantKey(charge.description()))
            && !dateOf(charge).isBefore(tracked.initialDate()) && !dateOf(charge).isAfter(today))
        .max(Comparator.comparing(Charge::date).thenComparing(Charge::id));
    var lastDate = last.map(TrackedSubscriptions::dateOf).orElse(tracked.initialDate());
    var nextMonth = YearMonth.from(lastDate).plusMonths(1);
    var next = nextMonth.atDay(Math.min(tracked.anchorDay(), nextMonth.lengthOfMonth()));
    return new Subscription(tracked.id(), tracked.description(), tracked.source(),
        last.map(Charge::accountLabel).orElse(tracked.accountLabel()), tracked.currency(),
        last.map(Charge::amount).orElse(tracked.initialAmount()), lastDate, next,
        last.map(Charge::id).orElse(null), available);
  }

  static String merchantKey(String description) {
    return description == null ? "" : Normalizer.normalize(description, Normalizer.Form.NFKC)
        .strip().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
  }

  private static LocalDate dateOf(Charge charge) {
    return charge.date().atZone(DISPLAY_ZONE).toLocalDate();
  }

  private static ResponseStatusException missingCharge() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Cobrança importada indisponível.");
  }

  record Tracked(UUID id, String source, UUID accountRef, String description, String merchantKey,
                 String currency, int anchorDay, BigDecimal initialAmount, LocalDate initialDate,
                 String accountLabel) {}

  record Charge(String id, UUID accountRef, String description, BigDecimal amount, String currency,
                Instant date, String accountLabel) {}

  public record Subscription(UUID id, String description, String source, String accountLabel,
                             String currency, BigDecimal amount, LocalDate lastChargeDate,
                             LocalDate nextChargeDate, String lastChargeId, boolean sourceAvailable) {}
}
