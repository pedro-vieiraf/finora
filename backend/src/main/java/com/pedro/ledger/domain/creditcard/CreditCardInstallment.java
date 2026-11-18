package com.pedro.ledger.domain.creditcard;

import com.pedro.ledger.domain.money.Money;
import java.util.UUID;

/**
 * Represents one installment of a credit card purchase.
 */
public class CreditCardInstallment {

  private final UUID id;
  private final int number;
  private final Money amount;
  private final UUID invoiceId;

  /**
   * Creates an installment.
   *
   * @param id installment identifier
   * @param number one-based installment number
   * @param amount installment amount
   * @param invoiceId invoice that receives the installment
   */
  public CreditCardInstallment(
      UUID id,
      int number,
      Money amount,
      UUID invoiceId
  ) {
    if (id == null) {
      throw new IllegalArgumentException(
          "Installment ID cannot be null"
      );
    }

    if (number < 1) {
      throw new IllegalArgumentException(
          "Installment number must be at least 1"
      );
    }

    if (amount == null) {
      throw new IllegalArgumentException(
          "Installment amount cannot be null"
      );
    }

    if (amount.isZero() || amount.isNegative()) {
      throw new IllegalArgumentException(
          "Installment amount must be greater than zero"
      );
    }

    if (invoiceId == null) {
      throw new IllegalArgumentException(
          "Installment invoice ID cannot be null"
      );
    }

    this.id = id;
    this.number = number;
    this.amount = amount;
    this.invoiceId = invoiceId;
  }

  public UUID getId() {
    return id;
  }

  public int getNumber() {
    return number;
  }

  public Money getAmount() {
    return amount;
  }

  public UUID getInvoiceId() {
    return invoiceId;
  }
}
