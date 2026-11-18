package com.pedro.ledger.domain.creditcard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class CreditCardBillingCycleTest {

  @Test
  void shouldUseClosingDayInclusively() {
    LocalDate closingDay = LocalDate.of(2026, 5, 10);

    assertThat(CreditCardBillingCycle.closingDateFor(
        LocalDate.of(2026, 5, 9),
        10
    )).isEqualTo(closingDay);

    assertThat(CreditCardBillingCycle.closingDateFor(
        closingDay,
        10
    )).isEqualTo(closingDay);

    assertThat(CreditCardBillingCycle.closingDateFor(
        LocalDate.of(2026, 5, 11),
        10
    )).isEqualTo(LocalDate.of(2026, 6, 10));
  }

  @Test
  void shouldClampClosingDayToShortMonths() {
    assertThat(CreditCardBillingCycle.closingDate(
        YearMonth.of(2026, 2),
        31
    )).isEqualTo(LocalDate.of(2026, 2, 28));

    assertThat(CreditCardBillingCycle.closingDateFor(
        LocalDate.of(2026, 2, 28),
        31
    )).isEqualTo(LocalDate.of(2026, 2, 28));

    assertThat(CreditCardBillingCycle.closingDateFor(
        LocalDate.of(2026, 3, 1),
        31
    )).isEqualTo(LocalDate.of(2026, 3, 31));
  }

  @Test
  void shouldRespectLeapYear() {
    assertThat(CreditCardBillingCycle.closingDate(
        YearMonth.of(2028, 2),
        29
    )).isEqualTo(LocalDate.of(2028, 2, 29));
  }

  @Test
  void shouldCalculateCycleStartAfterPreviousClosing() {
    assertThat(CreditCardBillingCycle.cycleStart(
        LocalDate.of(2026, 2, 28),
        31
    )).isEqualTo(LocalDate.of(2026, 2, 1));

    assertThat(CreditCardBillingCycle.cycleStart(
        LocalDate.of(2026, 2, 28),
        30
    )).isEqualTo(LocalDate.of(2026, 1, 31));
  }

  @Test
  void shouldCalculateDueDateInFollowingMonth() {
    assertThat(CreditCardBillingCycle.dueDate(
        LocalDate.of(2026, 1, 31),
        10
    )).isEqualTo(LocalDate.of(2026, 2, 10));

    assertThat(CreditCardBillingCycle.dueDate(
        LocalDate.of(2026, 1, 31),
        31
    )).isEqualTo(LocalDate.of(2026, 2, 28));
  }

  @Test
  void shouldCalculateInstallmentClosingDates() {
    LocalDate firstClosing = LocalDate.of(2026, 2, 28);

    assertThat(CreditCardBillingCycle.installmentClosingDate(
        firstClosing,
        0,
        31
    )).isEqualTo(LocalDate.of(2026, 2, 28));

    assertThat(CreditCardBillingCycle.installmentClosingDate(
        firstClosing,
        1,
        31
    )).isEqualTo(LocalDate.of(2026, 3, 31));
  }

  @Test
  void shouldHandleYearTransition() {
    assertThat(CreditCardBillingCycle.closingDateFor(
        LocalDate.of(2026, 12, 31),
        31
    )).isEqualTo(LocalDate.of(2026, 12, 31));

    assertThat(CreditCardBillingCycle.closingDateFor(
        LocalDate.of(2027, 1, 1),
        31
    )).isEqualTo(LocalDate.of(2027, 1, 31));

    assertThat(CreditCardBillingCycle.cycleStart(
        LocalDate.of(2027, 1, 31),
        31
    )).isEqualTo(LocalDate.of(2027, 1, 1));

    assertThat(CreditCardBillingCycle.dueDate(
        LocalDate.of(2026, 12, 31),
        10
    )).isEqualTo(LocalDate.of(2027, 1, 10));

    assertThat(CreditCardBillingCycle.installmentClosingDate(
        LocalDate.of(2026, 12, 31),
        1,
        31
    )).isEqualTo(LocalDate.of(2027, 1, 31));
  }

  @Test
  void shouldRejectInvalidDaysAndDates() {
    assertThatThrownBy(() -> CreditCardBillingCycle.closingDateFor(
        LocalDate.of(2026, 5, 1),
        0
    )).isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(() -> CreditCardBillingCycle.closingDateFor(
        LocalDate.of(2026, 5, 1),
        32
    )).isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(() -> CreditCardBillingCycle.closingDateFor(
        null,
        10
    )).isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(() -> CreditCardBillingCycle.installmentClosingDate(
        LocalDate.of(2026, 5, 10),
        -1,
        10
    )).isInstanceOf(IllegalArgumentException.class);
  }
}
