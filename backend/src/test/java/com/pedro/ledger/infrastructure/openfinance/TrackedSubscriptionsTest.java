package com.pedro.ledger.infrastructure.openfinance;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

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

  private static TrackedSubscriptions.Charge charge(String id, UUID account, String description,
      String currency, String date) {
    return new TrackedSubscriptions.Charge(id, account, description, new BigDecimal("44.90"),
        currency, Instant.parse(date), "Cartão");
  }
}
