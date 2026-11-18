package com.pedro.ledger.domain.creditcard;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Calculates credit card billing cycle dates.
 *
 * <p>Rules:
 * <ul>
 *   <li>The closing day is clamped to the last day of the month when the
 *       configured day does not exist (for example, day 31 in February).</li>
 *   <li>A purchase made on the closing day belongs to the invoice that closes
 *       on that day. Purchases made after the closing day belong to the next
 *       invoice.</li>
 *   <li>The cycle starts on the day after the previous invoice closing date.</li>
 *   <li>The due date falls in the month following the closing date, also
 *       clamped to the last day of that month.</li>
 * </ul>
 */
public final class CreditCardBillingCycle {

  private CreditCardBillingCycle() {
  }

  /**
   * Finds the closing date of the invoice that receives a purchase.
   *
   * @param purchaseDate purchase date
   * @param closingDay configured closing day
   * @return closing date of the invoice that contains the purchase
   */
  public static LocalDate closingDateFor(
      LocalDate purchaseDate,
      int closingDay
  ) {
    validateDate(purchaseDate, "Purchase date");
    validateDay(closingDay, "Closing day");

    YearMonth month = YearMonth.from(purchaseDate);
    LocalDate closingDate = closingDate(month, closingDay);

    if (purchaseDate.isAfter(closingDate)) {
      closingDate = closingDate(month.plusMonths(1), closingDay);
    }

    return closingDate;
  }

  /**
   * Calculates the closing date for a month, clamping the configured day.
   *
   * @param month invoice month
   * @param closingDay configured closing day
   * @return closing date within the month
   */
  public static LocalDate closingDate(YearMonth month, int closingDay) {
    if (month == null) {
      throw new IllegalArgumentException("Invoice month cannot be null");
    }

    validateDay(closingDay, "Closing day");

    return month.atDay(Math.min(closingDay, month.lengthOfMonth()));
  }

  /**
   * Calculates the first day of an invoice cycle.
   *
   * @param closingDate invoice closing date
   * @param closingDay configured closing day
   * @return the day after the previous closing date
   */
  public static LocalDate cycleStart(
      LocalDate closingDate,
      int closingDay
  ) {
    validateDate(closingDate, "Closing date");
    validateDay(closingDay, "Closing day");

    YearMonth previousMonth = YearMonth.from(closingDate).minusMonths(1);

    return closingDate(previousMonth, closingDay).plusDays(1);
  }

  /**
   * Calculates the invoice due date in the month after closing.
   *
   * @param closingDate invoice closing date
   * @param dueDay configured due day
   * @return due date
   */
  public static LocalDate dueDate(LocalDate closingDate, int dueDay) {
    validateDate(closingDate, "Closing date");
    validateDay(dueDay, "Due day");

    YearMonth dueMonth = YearMonth.from(closingDate).plusMonths(1);

    return dueMonth.atDay(Math.min(dueDay, dueMonth.lengthOfMonth()));
  }

  /**
   * Calculates the closing date of a later installment invoice.
   *
   * @param firstClosingDate closing date of the first installment
   * @param installmentIndex zero-based installment index
   * @param closingDay configured closing day
   * @return closing date of the installment invoice
   */
  public static LocalDate installmentClosingDate(
      LocalDate firstClosingDate,
      int installmentIndex,
      int closingDay
  ) {
    validateDate(firstClosingDate, "First closing date");

    if (installmentIndex < 0) {
      throw new IllegalArgumentException(
          "Installment index cannot be negative"
      );
    }

    YearMonth month = YearMonth.from(firstClosingDate)
        .plusMonths(installmentIndex);

    return closingDate(month, closingDay);
  }

  /**
   * Validates a credit card day of month.
   *
   * @param day day of month
   * @param field field name used in the error message
   */
  public static void validateDay(int day, String field) {
    if (day < 1 || day > 31) {
      throw new IllegalArgumentException(
          field + " must be between 1 and 31"
      );
    }
  }

  private static void validateDate(LocalDate date, String field) {
    if (date == null) {
      throw new IllegalArgumentException(field + " cannot be null");
    }
  }
}
