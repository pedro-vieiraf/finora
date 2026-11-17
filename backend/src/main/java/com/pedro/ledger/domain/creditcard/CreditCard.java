package com.pedro.ledger.domain.creditcard;

import com.pedro.ledger.domain.money.CurrencyMismatchException;
import com.pedro.ledger.domain.money.Money;
import java.util.Currency;
import java.util.UUID;

/**
 * Represents a credit card.
 *
 * <p>A credit card is intentionally separate from a bank account. It owns a
 * credit limit and is settled through invoices; it never exposes an account
 * balance.
 */
public class CreditCard {

  private final UUID id;
  private String name;
  private String issuer;
  private Money creditLimit;
  private int closingDay;
  private int dueDay;
  private CreditCardStatus status;

  private CreditCard(
      UUID id,
      String name,
      String issuer,
      Money creditLimit,
      int closingDay,
      int dueDay,
      CreditCardStatus status
  ) {
    this.id = id;
    this.name = name;
    this.issuer = issuer;
    this.creditLimit = creditLimit;
    this.closingDay = closingDay;
    this.dueDay = dueDay;
    this.status = status;
  }

  /**
   * Opens a new active credit card.
   *
   * @param name card name
   * @param issuer issuing institution
   * @param creditLimit total credit limit
   * @param closingDay invoice closing day
   * @param dueDay invoice due day
   * @return a new credit card
   */
  public static CreditCard open(
      String name,
      String issuer,
      Money creditLimit,
      int closingDay,
      int dueDay
  ) {
    validateName(name);
    validateIssuer(issuer);
    validateCreditLimit(creditLimit);
    CreditCardBillingCycle.validateDay(closingDay, "Closing day");
    CreditCardBillingCycle.validateDay(dueDay, "Due day");

    return new CreditCard(
        UUID.randomUUID(),
        name.trim(),
        issuer.trim(),
        creditLimit,
        closingDay,
        dueDay,
        CreditCardStatus.ACTIVE
    );
  }

  /**
   * Restores a credit card from persisted data.
   *
   * @param id card identifier
   * @param name card name
   * @param issuer issuing institution
   * @param creditLimit total credit limit
   * @param closingDay invoice closing day
   * @param dueDay invoice due day
   * @param status card status
   * @return a restored credit card
   */
  public static CreditCard restore(
      UUID id,
      String name,
      String issuer,
      Money creditLimit,
      int closingDay,
      int dueDay,
      CreditCardStatus status
  ) {
    validateId(id);
    validateName(name);
    validateIssuer(issuer);
    validateCreditLimit(creditLimit);
    CreditCardBillingCycle.validateDay(closingDay, "Closing day");
    CreditCardBillingCycle.validateDay(dueDay, "Due day");
    validateStatus(status);

    return new CreditCard(
        id,
        name.trim(),
        issuer.trim(),
        creditLimit,
        closingDay,
        dueDay,
        status
    );
  }

  /**
   * Calculates the available credit limit.
   *
   * <p>The committed amount is the outstanding balance of all invoices,
   * including unpaid installments from current and future invoices.
   *
   * @param committedAmount amount already committed
   * @return available credit limit
   */
  public Money availableLimit(Money committedAmount) {
    validateCommittedAmount(committedAmount);

    return creditLimit.subtract(committedAmount);
  }

  /**
   * Ensures a purchase fits within the available credit limit.
   *
   * @param purchaseAmount purchase total
   * @param committedAmount amount already committed
   * @throws CreditCardLimitExceededException if the purchase exceeds the limit
   */
  public void ensureCanPurchase(
      Money purchaseAmount,
      Money committedAmount
  ) {
    validateOperationAmount(purchaseAmount);
    validateCommittedAmount(committedAmount);

    if (purchaseAmount.isGreaterThan(availableLimit(committedAmount))) {
      throw new CreditCardLimitExceededException(
          "Purchase exceeds the available credit limit"
      );
    }
  }

  /**
   * Ensures the card is active.
   *
   * @throws CreditCardInactiveException if the card is inactive
   */
  public void ensureActive() {
    if (status != CreditCardStatus.ACTIVE) {
      throw new CreditCardInactiveException("Credit card is inactive");
    }
  }

  /**
   * Changes the card name.
   *
   * @param newName new card name
   */
  public void rename(String newName) {
    ensureActive();
    validateName(newName);

    name = newName.trim();
  }

  /**
   * Changes the issuing institution.
   *
   * @param newIssuer new issuing institution
   */
  public void changeIssuer(String newIssuer) {
    ensureActive();
    validateIssuer(newIssuer);

    issuer = newIssuer.trim();
  }

  /**
   * Changes the credit limit.
   *
   * @param newLimit new credit limit
   * @param committedAmount amount already committed
   */
  public void changeLimit(Money newLimit, Money committedAmount) {
    ensureActive();
    validateCreditLimit(newLimit);
    validateCommittedAmount(committedAmount);

    if (newLimit.isLessThan(committedAmount)) {
      throw new IllegalArgumentException(
          "Credit limit cannot be lower than the committed amount"
      );
    }

    creditLimit = newLimit;
  }

  /**
   * Changes the invoice closing day.
   *
   * <p>The change only affects invoices created after the update. Existing
   * invoices and installments keep their original cycle dates.
   *
   * @param newClosingDay new closing day
   */
  public void changeClosingDay(int newClosingDay) {
    ensureActive();
    CreditCardBillingCycle.validateDay(newClosingDay, "Closing day");

    closingDay = newClosingDay;
  }

  /**
   * Changes the invoice due day.
   *
   * @param newDueDay new due day
   */
  public void changeDueDay(int newDueDay) {
    ensureActive();
    CreditCardBillingCycle.validateDay(newDueDay, "Due day");

    dueDay = newDueDay;
  }

  /**
   * Deactivates the card. Historical data is preserved.
   */
  public void deactivate() {
    if (status == CreditCardStatus.INACTIVE) {
      throw new IllegalStateException("Credit card is already inactive");
    }

    status = CreditCardStatus.INACTIVE;
  }

  /**
   * Reactivates the card.
   */
  public void activate() {
    if (status == CreditCardStatus.ACTIVE) {
      throw new IllegalStateException("Credit card is already active");
    }

    status = CreditCardStatus.ACTIVE;
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getIssuer() {
    return issuer;
  }

  public Money getCreditLimit() {
    return creditLimit;
  }

  public int getClosingDay() {
    return closingDay;
  }

  public int getDueDay() {
    return dueDay;
  }

  public CreditCardStatus getStatus() {
    return status;
  }

  public Currency getCurrency() {
    return creditLimit.currency();
  }

  public boolean isActive() {
    return status == CreditCardStatus.ACTIVE;
  }

  private static void validateId(UUID id) {
    if (id == null) {
      throw new IllegalArgumentException("Credit card ID cannot be null");
    }
  }

  private static void validateName(String name) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException(
          "Credit card name cannot be null or blank"
      );
    }
  }

  private static void validateIssuer(String issuer) {
    if (issuer == null || issuer.isBlank()) {
      throw new IllegalArgumentException(
          "Credit card issuer cannot be null or blank"
      );
    }
  }

  private static void validateCreditLimit(Money creditLimit) {
    if (creditLimit == null) {
      throw new IllegalArgumentException(
          "Credit limit cannot be null"
      );
    }

    if (creditLimit.isZero() || creditLimit.isNegative()) {
      throw new IllegalArgumentException(
          "Credit limit must be greater than zero"
      );
    }
  }

  private static void validateStatus(CreditCardStatus status) {
    if (status == null) {
      throw new IllegalArgumentException(
          "Credit card status cannot be null"
      );
    }
  }

  private void validateCommittedAmount(Money committedAmount) {
    if (committedAmount == null) {
      throw new IllegalArgumentException(
          "Committed amount cannot be null"
      );
    }

    if (committedAmount.isNegative()) {
      throw new IllegalArgumentException(
          "Committed amount cannot be negative"
      );
    }

    if (!committedAmount.currency().equals(creditLimit.currency())) {
      throw new CurrencyMismatchException(
          committedAmount.currency(),
          creditLimit.currency()
      );
    }
  }

  private void validateOperationAmount(Money amount) {
    if (amount == null) {
      throw new IllegalArgumentException("Amount cannot be null");
    }

    if (amount.isZero() || amount.isNegative()) {
      throw new IllegalArgumentException(
          "Amount must be greater than zero"
      );
    }

    if (!amount.currency().equals(creditLimit.currency())) {
      throw new CurrencyMismatchException(
          amount.currency(),
          creditLimit.currency()
      );
    }
  }
}
