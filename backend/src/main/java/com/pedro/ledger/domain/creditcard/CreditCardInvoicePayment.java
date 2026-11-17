package com.pedro.ledger.domain.creditcard;

import com.pedro.ledger.domain.money.Money;
import java.time.Instant;
import java.util.UUID;

/**
 * Represents a payment made against a credit card invoice.
 *
 * <p>The payment is the traceable record of the bank account debit. It is not
 * a bank transaction and must not create a duplicate expense.
 */
public class CreditCardInvoicePayment {

  private final UUID id;
  private final UUID invoiceId;
  private final UUID accountId;
  private final Money amount;
  private final Instant paidAt;

  private CreditCardInvoicePayment(
      UUID id,
      UUID invoiceId,
      UUID accountId,
      Money amount,
      Instant paidAt
  ) {
    this.id = id;
    this.invoiceId = invoiceId;
    this.accountId = accountId;
    this.amount = amount;
    this.paidAt = paidAt;
  }

  /**
   * Creates an invoice payment.
   *
   * @param id payment identifier
   * @param invoiceId invoice identifier
   * @param accountId source bank account identifier
   * @param amount paid amount
   * @param paidAt payment instant
   * @return a new invoice payment
   */
  public static CreditCardInvoicePayment create(
      UUID id,
      UUID invoiceId,
      UUID accountId,
      Money amount,
      Instant paidAt
  ) {
    if (id == null) {
      throw new IllegalArgumentException(
          "Payment ID cannot be null"
      );
    }

    if (invoiceId == null) {
      throw new IllegalArgumentException(
          "Payment invoice ID cannot be null"
      );
    }

    if (accountId == null) {
      throw new IllegalArgumentException(
          "Payment account ID cannot be null"
      );
    }

    if (amount == null) {
      throw new IllegalArgumentException(
          "Payment amount cannot be null"
      );
    }

    if (amount.isZero() || amount.isNegative()) {
      throw new IllegalArgumentException(
          "Payment amount must be greater than zero"
      );
    }

    if (paidAt == null) {
      throw new IllegalArgumentException(
          "Payment instant cannot be null"
      );
    }

    return new CreditCardInvoicePayment(
        id,
        invoiceId,
        accountId,
        amount,
        paidAt
    );
  }

  public UUID getId() {
    return id;
  }

  public UUID getInvoiceId() {
    return invoiceId;
  }

  public UUID getAccountId() {
    return accountId;
  }

  public Money getAmount() {
    return amount;
  }

  public Instant getPaidAt() {
    return paidAt;
  }
}
