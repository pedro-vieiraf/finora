package com.pedro.ledger.infrastructure.openfinance;

import static org.assertj.core.api.Assertions.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class TrackedSubscriptionsTest {

  private final UUID account = UUID.randomUUID();
  private final TrackedSubscriptions.Tracked tracked = new TrackedSubscriptions.Tracked(
      UUID.randomUUID(), "CARD", account, "Netflix", "NETFLIX", "BRL", 31,
      new BigDecimal("39.90"), LocalDate.parse("2026-01-31"), "Cartão");

  @Test
  void predictsTheNextMonthClampingToItsLastDay() {
    var summary = TrackedSubscriptions.summarize(tracked, List.of(), LocalDate.parse("2026-02-01"), false);
    assertThat(summary.lastChargeDate()).isEqualTo("2026-01-31");
    assertThat(summary.nextChargeDate()).isEqualTo("2026-02-28");
    assertThat(summary.amount()).isEqualByComparingTo("39.90");
    assertThat(summary.sourceAvailable()).isFalse();
  }

  @Test
  void followsAnObservedChargeWithANewPriceAndKeepsTheOriginalBillingDay() {
    var summary = TrackedSubscriptions.summarize(tracked, List.of(
        charge("actual", account, "  netflix ", "BRL", "2026-03-01T01:00:00Z"),
        charge("other-account", UUID.randomUUID(), "Netflix", "BRL", "2026-03-01T13:00:00Z"),
        charge("other-currency", account, "Netflix", "USD", "2026-03-01T13:00:00Z"),
        charge("unrelated", account, "Another service", "BRL", "2026-03-01T13:00:00Z"),
        charge("future", account, "Netflix", "BRL", "2026-04-01T13:00:00Z")
    ), LocalDate.parse("2026-03-01"), true);

    assertThat(summary.lastChargeId()).isEqualTo("actual");
    assertThat(summary.lastChargeDate()).isEqualTo("2026-02-28");
    assertThat(summary.amount()).isEqualByComparingTo("44.90");
    assertThat(summary.nextChargeDate()).isEqualTo("2026-03-31");
  }

  @Test
  void registersAnEligiblePendingDebitFromTheBankBill() {
    var bills = mock(ConnectedCardBills.class);
    var jdbc = mock(JdbcTemplate.class);
    var entry = new ConnectedCardBills.Entry("entry", Instant.parse("2026-01-31T12:00:00Z"),
        "Netflix", "DEBIT", new BigDecimal("39.90"), null, 1, 1, true);
    var bill = new ConnectedCardBills.Bill("bill", LocalDate.parse("2026-02-05"),
        null, null, "BRL", List.of(), List.of(entry));
    when(bills.findAll()).thenReturn(List.of(new ConnectedCardBills.Card(account, "Cartão",
        null, "BRL", null, null, List.of(bill), List.of())));
    when(bills.confirmedCharges()).thenReturn(Map.of());

    new TrackedSubscriptions(jdbc, bills, Clock.systemUTC()).trackCard(account, "entry");

    verify(jdbc).update(contains("INSERT INTO open_finance_tracked_subscriptions"),
        any(UUID.class), eq("CARD"), eq(account), eq("Netflix"), eq("NETFLIX"), eq("BRL"),
        eq(31), eq(new BigDecimal("39.90")), eq(LocalDate.parse("2026-01-31")), eq("Cartão"));
  }

  @Test
  void retainsTheLatestObservedCardPriceWhenTheProviderBecomesUnavailable() {
    var bills = mock(ConnectedCardBills.class);
    var jdbc = mock(JdbcTemplate.class);
    var newer = new TrackedSubscriptions.Tracked(tracked.id(), "CARD", account, "Netflix",
        "NETFLIX", "BRL", 31, new BigDecimal("44.90"), LocalDate.parse("2026-02-28"), "Cartão");
    when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<TrackedSubscriptions.Tracked>>any()))
        .thenReturn(List.of(tracked), List.of(newer));
    when(bills.confirmedCharges()).thenReturn(Map.of(account, List.of(
        new ConnectedCardBills.Entry("new", Instant.parse("2026-02-28T12:00:00Z"),
            "Netflix", "DEBIT", new BigDecimal("44.90"), "BRL", null, null))))
        .thenThrow(new IllegalStateException("Provider unavailable"));
    var service = new TrackedSubscriptions(jdbc, bills,
        Clock.fixed(Instant.parse("2026-03-01T12:00:00Z"), ZoneOffset.UTC));

    assertThat(service.findAll().getFirst().amount()).isEqualByComparingTo("44.90");
    verify(jdbc).update(contains("SET initial_amount"), eq(new BigDecimal("44.90")),
        eq(LocalDate.parse("2026-02-28")), eq(tracked.id()),
        eq(tracked.initialDate()), eq(tracked.initialAmount()));
    var fallback = service.findAll().getFirst();
    assertThat(fallback.amount()).isEqualByComparingTo("44.90");
    assertThat(fallback.nextChargeDate()).isEqualTo("2026-03-31");
    assertThat(fallback.sourceAvailable()).isFalse();
  }

  private static TrackedSubscriptions.Charge charge(String id, UUID account, String description,
      String currency, String date) {
    return new TrackedSubscriptions.Charge(id, account, description, new BigDecimal("44.90"),
        currency, Instant.parse(date), "Cartão");
  }
}
