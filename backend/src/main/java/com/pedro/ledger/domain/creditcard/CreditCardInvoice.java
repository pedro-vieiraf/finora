package com.pedro.ledger.domain.creditcard;

import com.pedro.ledger.domain.money.CurrencyMismatchException;
import com.pedro.ledger.domain.money.Money;
import java.time.LocalDate;
import java.util.Currency;
import java.util.UUID;

/**
 * Represents a credit card invoice for a single billing cycle.
 */
public class CreditCardInvoice {

  private final UUID id;
  private final UUID cardId;
  private final LocalDate cycleStart;
  private final LocalDate cycleEnd;
  private final LocalDate closingDate;
  private final LocalDate dueDate;
  private final Currency currency;
  private Money total;
  private Money paid;
  private CreditCardInvoiceStatus status;

  private CreditCardInvoice(
      UUID id,
      UUID cardId,
      LocalDate cycleStart,
      LocalDate cycleEnd,
      LocalDate closingDate,
      LocalDate dueDate,
      Currency currency,
      Money total,
      Money paid,
      CreditCardInvoiceStatus status
  ) {
    this.id = id;
    this.cardId = cardId;
    this.cycleStart = cycleStart;
    this.cycleEnd = cycleEnd;
    this.closingDate = closingDate;
    this.dueDate = dueDate;
    this.currency = currency;
    this.total = total;
    this.paid = paid;
    this.status = status;
  }

  /**
   * Opens a new invoice with no charges.
   *
   * @param cardId card identifier
   * @param cycleStart cycle start date
   * @param cycleEnd cycle end date
   * @param closingDate closing date
   * @param dueDate due date
   * @param currency invoice currency
   * @param today current date used to derive the initial status
   * @return a new invoice
   */
  public static CreditCardInvoice open(
      UUID cardId,
      LocalDate cycleStart,
      LocalDate cycleEnd,
      LocalDate closingDate,
      LocalDate dueDate,
      Currency currency,
      LocalDate today
  ) {
    validateCardId(cardId);
    validateDates(cycleStart, cycleEnd, closingDate, dueDate);
    validateCurrency(currency);
    validateDate(today, "Current date");

    CreditCardInvoice invoice = new CreditCardInvoice(
        UUID.randomUUID(),
        cardId,
        cycleStart,
        cycleEnd,
        closingDate,
        dueDate,
        currency,
        Money.zero(currency),
        Money.zero(currency),
        CreditCardInvoiceStatus.OPEN
    );

    invoice.refreshStatus(today);

    return invoice;
  }

  /**
   * Restores an invoice from persisted data.
   *
   * @param id invoice identifier
   * @param cardId card identifier
   * @param cycleStart cycle start date
   * @param cycleEnd cycle end date
   * @param closingDate closing date
   * @param dueDate due date
   * @param currency invoice currency
   * @param total total amount
   * @param paid paid amount
   * @param status invoice status
   * @return a restored invoice
   */
  public static CreditCardInvoice restore(
      UUID id,
      UUID cardId,
      LocalDate cycleStart,
      LocalDate cycleEnd,
      LocalDate closingDate,
      LocalDate dueDate,
      Currency currency,
      Money total,
      Money paid,
      CreditCardInvoiceStatus status
  ) {
    validateId(id);
    validateCardId(cardId);
    validateDates(cycleStart, cycleEnd, closingDate, dueDate);
    validateCurrency(currency);
    validateAmount(total, "Total amount");
    validateAmount(paid, "Paid amount");
    validateSameCurrency(total, currency);
    validateSameCurrency(paid, currency);
    validateStatus(status);

    if (paid.isGreaterThan(total)) {
      throw new IllegalArgumentException(
          "Paid amount cannot exceed the total amount"
      );
    }

    return new CreditCardInvoice(
        id,
        cardId,
        cycleStart,
        cycleEnd,
        closingDate,
        dueDate,
        currency,
        total,
        paid,
        status
    );
  }

  /**
   * Recalculates the status from the current date and paid amount.
   *
   * @param today current date
   */
  public void refreshStatus(LocalDate today) {
    validateDate(today, "Current date");

    if (!paid.isZero()) {
      status = paid.equals(total)
          ? CreditCardInvoiceStatus.PAID
          : CreditCardInvoiceStatus.PARTIALLY_PAID;

      return;
    }

    status = closingDate.isBefore(today)
        ? CreditCardInvoiceStatus.CLOSED
        : CreditCardInvoiceStatus.OPEN;
  }

  /**
   * Adds a charge to the invoice.
   *
   * @param amount charge amount
   * @param today current date
   */
  public void addCharge(Money amount, LocalDate today) {
    validatePositiveAmount(amount);
    validateSameCurrency(amount, currency);
    validateDate(today, "Current date");

    if (status == CreditCardInvoiceStatus.PAID) {
      throw new IllegalStateException(
          "Paid invoices cannot receive new charges"
      );
    }

    total = total.add(amount);
    refreshStatus(today);
  }

  /**
   * Removes a charge from the invoice.
   *
   * <p>Removal is only allowed while the invoice is open and no payment has
   * been made, preserving the financial history of paid or partially paid
   * invoices.
   *
   * @param amount charge amount
   */
  public void removeCharge(Money amount) {
    validatePositiveAmount(amount);
    validateSameCurrency(amount, currency);

    if (status != CreditCardInvoiceStatus.OPEN) {
      throw new IllegalStateException(
          "Only open invoices can have charges removed"
      );
    }

    if (!paid.isZero()) {
      throw new IllegalStateException(
          "Invoices with payments cannot have charges removed"
      );
    }

    if (amount.isGreaterThan(total)) {
      throw new IllegalArgumentException(
          "Charge amount cannot exceed the invoice total"
      );
    }

    total = total.subtract(amount);
  }

  /**
   * Registers a payment against the invoice.
   *
   * @param amount payment amount
   */
  public void registerPayment(Money amount) {
    validatePositiveAmount(amount);
    validateSameCurrency(amount, currency);

    if (status == CreditCardInvoiceStatus.OPEN) {
      throw new IllegalStateException(
          "Invoices can only be paid after closing"
      );
    }

    if (status == CreditCardInvoiceStatus.PAID) {
      throw new IllegalStateException("Invoice is already paid");
    }

    if (amount.isGreaterThan(getRemaining())) {
      throw new IllegalArgumentException(
          "Payment cannot exceed the remaining invoice amount"
      );
    }

    paid = paid.add(amount);
    status = paid.equals(total)
        ? CreditCardInvoiceStatus.PAID
        : CreditCardInvoiceStatus.PARTIALLY_PAID;
  }

  public UUID getId() {
    return id;
  }

  public UUID getCardId() {
    return cardId;
  }

  public LocalDate getCycleStart() {
    return cycleStart;
  }

  public LocalDate getCycleEnd() {
    return cycleEnd;
  }

  public LocalDate getClosingDate() {
    return closingDate;
  }

  public LocalDate getDueDate() {
    return dueDate;
  }

  public Currency getCurrency() {
    return currency;
  }

  public Money getTotal() {
    return total;
  }

  public Money getPaid() {
    return paid;
  }

  public Money getRemaining() {
    return total.subtract(paid);
  }

  public CreditCardInvoiceStatus getStatus() {
    return status;
  }

  public boolean isOpen() {
    return status == CreditCardInvoiceStatus.OPEN;
  }

  public boolean isPaid() {
    return status == CreditCardInvoiceStatus.PAID;
  }

  public boolean hasPayments() {
    return !paid.isZero();
  }

  public boolean belongsTo(UUID expectedCardId) {
    return cardId.equals(expectedCardId);
  }

  private static void validateId(UUID id) {
    if (id == null) {
      throw new IllegalArgumentException("Invoice ID cannot be null");
    }
  }

  private static void validateCardId(UUID cardId) {
    if (cardId == null) {
      throw new IllegalArgumentException(
          "Invoice card ID cannot be null"
      );
    }
  }

  private static void validateDates(
      LocalDate cycleStart,
      LocalDate cycleEnd,
      LocalDate closingDate,
      LocalDate dueDate
  ) {
    validateDate(cycleStart, "Cycle start date");
    validateDate(cycleEnd, "Cycle end date");
    validateDate(closingDate, "Closing date");
    validateDate(dueDate, "Due date");

    if (cycleStart.isAfter(cycleEnd)) {
      throw new IllegalArgumentException(
          "Cycle start date cannot be after the cycle end date"
      );
    }

    if (!cycleEnd.equals(closingDate)) {
      throw new IllegalArgumentException(
          "Cycle end date must match the closing date"
      );
    }

    if (!dueDate.isAfter(closingDate)) {
      throw new IllegalArgumentException(
          "Due date must be after the closing date"
      );
    }
  }

  private static void validateDate(LocalDate date, String field) {
    if (date == null) {
      throw new IllegalArgumentException(field + " cannot be null");
    }
  }

  private static void validateCurrency(Currency currency) {
    if (currency == null) {
      throw new IllegalArgumentException("Currency cannot be null");
    }
  }

  private static void validateStatus(CreditCardInvoiceStatus status) {
    if (status == null) {
      throw new IllegalArgumentException(
          "Invoice status cannot be null"
      );
    }
  }

  private static void validateAmount(Money amount, String field) {
    if (amount == null) {
      throw new IllegalArgumentException(field + " cannot be null");
    }

    if (amount.isNegative()) {
      throw new IllegalArgumentException(field + " cannot be negative");
    }
  }

  private static void validatePositiveAmount(Money amount) {
    if (amount == null) {
      throw new IllegalArgumentException("Amount cannot be null");
    }

    if (amount.isZero() || amount.isNegative()) {
      throw new IllegalArgumentException(
          "Amount must be greater than zero"
      );
    }
  }

  private static void validateSameCurrency(
      Money amount,
      Currency expectedCurrency
  ) {
    if (!amount.currency().equals(expectedCurrency)) {
      throw new CurrencyMismatchException(
          amount.currency(),
          expectedCurrency
      );
    }
  }
}
