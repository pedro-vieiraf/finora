package com.pedro.ledger.infrastructure.openfinance;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ConnectedCardBills {

  private static final Duration CACHE_DURATION = Duration.ofMinutes(10);

  private final OpenFinanceImportStore store;
  private final Clock clock;
  private final PluggyClient client;

  private Instant refreshAfter = Instant.MIN;
  private List<Card> cached = List.of();

  @Autowired
  public ConnectedCardBills(
      OpenFinanceImportStore store, Clock clock,
      @Value("${PLUGGY_CLIENT_ID:}") String clientId,
      @Value("${PLUGGY_CLIENT_SECRET:}") String clientSecret
  ) {
    this(store, clock, clientId.isBlank() || clientSecret.isBlank()
        ? null : new PluggyClient(clientId, clientSecret));
  }

  ConnectedCardBills(OpenFinanceImportStore store, Clock clock, PluggyClient client) {
    this.store = store;
    this.clock = clock;
    this.client = client;
  }

  public synchronized List<Card> findAll() {
    if (client == null) {
      return List.of();
    }
    Instant now = clock.instant();
    if (now.isBefore(refreshAfter)) {
      return cached;
    }

    List<Card> cards = new ArrayList<>();
    var seenCards = new HashSet<UUID>();
    for (var bank : store.linkedBanks()) {
      for (var card : client.listCreditCards(bank.selection().itemId())) {
        if (seenCards.add(card.id())) {
          LocalDate today = now.atOffset(ZoneOffset.UTC).toLocalDate();
          var activity = readActivity(card, bank.cutoff(), today);
          var bills = activity.bills();
          LocalDate reportedDueDate = card.creditData() == null
              ? null : bankDate(card.creditData().balanceDueDate());
          LocalDate nextDueDate = reportedDueDate != null
              && (bills.isEmpty() || reportedDueDate.isAfter(bills.getFirst().dueDate()))
              ? reportedDueDate : null;
          BigDecimal openBalance = card.balance() != null && card.balance().signum() > 0
              ? card.balance() : null;
          YearMonth nextCycle = nextDueDate == null ? firstUnissuedMonth(bills, today) : YearMonth.from(nextDueDate);
          cards.add(new Card(card.id(), bank.selection().name(), openBalance,
              card.currencyCode(), nextDueDate, nextCycle.toString(), bills, activity.forecasts()));
        }
      }
    }
    cached = List.copyOf(cards);
    refreshAfter = now.plus(CACHE_DURATION);
    return cached;
  }

  private CardActivity readActivity(PluggyClient.CreditAccount card, LocalDate cutoff, LocalDate today) {
    var bills = client.listCardBills(card.id()).stream()
        .filter(bill -> bill.dueDate() != null && !bankDate(bill.dueDate()).isBefore(cutoff))
        .toList();
    Map<String, PluggyClient.Transaction> transactions = new LinkedHashMap<>();
    Map<String, Map<String, Entry>> entries = new HashMap<>();
    for (var bill : bills) {
      entries.put(bill.id(), new LinkedHashMap<>());
    }
    var cursors = new HashSet<String>();
    var end = today.plusMonths(6);
    var page = client.listTransactions(card.id(), cutoff, end);
    while (true) {
      for (var transaction : page.results()) {
        transactions.merge(transaction.id(), transaction,
            (prior, latest) -> "POSTED".equals(prior.status()) ? prior : latest);
        var metadata = transaction.creditCardMetadata();
        if (metadata == null || !entries.containsKey(metadata.billId())) {
          continue;
        }
        entries.get(metadata.billId()).putIfAbsent(transaction.id(), new Entry(
            transaction.id(), transaction.date(), transaction.description(), transaction.type(),
            transaction.amount(), transaction.currencyCode(), metadata.installmentNumber(),
            metadata.totalInstallments()
        ));
      }
      if (page.next() == null) {
        break;
      }
      if (!cursors.add(page.next())) {
        throw new IllegalStateException("A Pluggy repetiu um cursor de lançamentos do cartão.");
      }
      page = client.nextPage(page);
    }

    var issued = bills.stream().map(bill -> new Bill(
        bill.id(), bankDate(bill.dueDate()), bankDate(bill.billClosingDate()), bill.totalAmount(),
        bill.totalAmountCurrencyCode() == null ? card.currencyCode() : bill.totalAmountCurrencyCode(),
        bill.payments() == null ? List.of() : bill.payments().stream()
            .map(payment -> new Payment(payment.id(), bankDate(payment.paymentDate()), payment.amount(),
                payment.currencyCode())).toList(),
        List.copyOf(entries.get(bill.id()).values())
    )).sorted(Comparator.comparing(Bill::dueDate).reversed()).toList();
    YearMonth firstMonth = firstUnissuedMonth(issued, today);
    YearMonth lastMonth = YearMonth.from(end);
    var reported = ConnectedCardForecast.from(transactions.values(), card.currencyCode(), firstMonth, lastMonth);
    var forecasts = ConnectedCardForecast.withInstallmentEstimates(
        reported, issued.isEmpty() ? null : issued.getFirst(), firstMonth, lastMonth,
        today, transactions.values());
    return new CardActivity(issued, forecasts);
  }

  private static YearMonth firstUnissuedMonth(List<Bill> bills, LocalDate today) {
    YearMonth current = YearMonth.from(today);
    if (bills.isEmpty()) {
      return current;
    }
    YearMonth afterLatest = YearMonth.from(bills.getFirst().dueDate()).plusMonths(1);
    return afterLatest.isAfter(current) ? afterLatest : current;
  }

  private record CardActivity(List<Bill> bills, List<Forecast> forecasts) {}

  public record Forecast(String month, BigDecimal total, String currency, boolean estimated) {}

  private static LocalDate bankDate(String value) {
    return value == null ? null : LocalDate.parse(value.substring(0, 10));
  }

  public record Card(
      UUID id, String bankName, BigDecimal openBalance, String currency,
      LocalDate nextDueDate, String nextCycle, List<Bill> bills, List<Forecast> forecasts
  ) {}

  public record Bill(
      String id, LocalDate dueDate, LocalDate closingDate, BigDecimal total, String currency,
      List<Payment> payments, List<Entry> entries
  ) {}

  public record Payment(String id, LocalDate date, BigDecimal amount, String currency) {}

  public record Entry(
      String id, Instant date, String description, String direction, BigDecimal amount, String currency,
      Integer installmentNumber, Integer totalInstallments
  ) {}
}
