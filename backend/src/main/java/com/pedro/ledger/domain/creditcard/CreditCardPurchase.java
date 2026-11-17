package com.pedro.ledger.domain.creditcard;

import com.pedro.ledger.domain.money.Money;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents a purchase made with a credit card.
 *
 * <p>A purchase is not a bank transaction. It accumulates in credit card
 * invoices and only affects the bank account when an invoice is paid.
 */
public class CreditCardPurchase {

  private final UUID id;
  private final UUID cardId;
  private final String description;
  private final Money totalAmount;
  private final LocalDate purchaseDate;
  private final UUID categoryId;
  private final int installmentCount;
  private final List<CreditCardInstallment> installments;
  private final Instant createdAt;
  private CreditCardPurchaseStatus status;

  private CreditCardPurchase(
      UUID id,
      UUID cardId,
      String description,
      Money totalAmount,
      LocalDate purchaseDate,
      UUID categoryId,
      int installmentCount,
      List<CreditCardInstallment> installments,
      Instant createdAt,
      CreditCardPurchaseStatus status
  ) {
    this.id = id;
    this.cardId = cardId;
    this.description = description;
    this.totalAmount = totalAmount;
    this.purchaseDate = purchaseDate;
    this.categoryId = categoryId;
    this.installmentCount = installmentCount;
    this.installments = installments;
    this.createdAt = createdAt;
    this.status = status;
  }

  /**
   * Creates a purchase and distributes its amount across installments.
   *
   * <p>Amounts are split in cents. The first installments receive the
   * remaining cents so the sum is always exactly equal to the purchase total.
   *
   * @param id purchase identifier
   * @param cardId card identifier
   * @param description purchase description
   * @param totalAmount purchase total
   * @param purchaseDate purchase date
   * @param categoryId optional category identifier
   * @param installmentCount number of installments
   * @param invoiceIds invoice identifier for each installment, in order
   * @param createdAt creation instant
   * @return a new purchase
   */
  public static CreditCardPurchase create(
      UUID id,
      UUID cardId,
      String description,
      Money totalAmount,
      LocalDate purchaseDate,
      UUID categoryId,
      int installmentCount,
      List<UUID> invoiceIds,
      Instant createdAt
  ) {
    validateId(id);
    validateCardId(cardId);
    validateDescription(description);
    validateTotalAmount(totalAmount);
    validatePurchaseDate(purchaseDate);
    validateInstallmentCount(installmentCount);
    validateInvoiceIds(invoiceIds, installmentCount);
    validateCreatedAt(createdAt);

    List<Money> amounts = splitAmount(totalAmount, installmentCount);
    List<CreditCardInstallment> installments = new ArrayList<>();

    for (int index = 0; index < installmentCount; index++) {
      installments.add(new CreditCardInstallment(
          UUID.randomUUID(),
          index + 1,
          amounts.get(index),
          invoiceIds.get(index)
      ));
    }

    return new CreditCardPurchase(
        id,
        cardId,
        description.trim(),
        totalAmount,
        purchaseDate,
        categoryId,
        installmentCount,
        List.copyOf(installments),
        createdAt,
        CreditCardPurchaseStatus.ACTIVE
    );
  }

  /**
   * Restores a purchase from persisted data.
   *
   * @param id purchase identifier
   * @param cardId card identifier
   * @param description purchase description
   * @param totalAmount purchase total
   * @param purchaseDate purchase date
   * @param categoryId optional category identifier
   * @param installmentCount number of installments
   * @param installments persisted installments
   * @param createdAt creation instant
   * @param status purchase status
   * @return a restored purchase
   */
  public static CreditCardPurchase restore(
      UUID id,
      UUID cardId,
      String description,
      Money totalAmount,
      LocalDate purchaseDate,
      UUID categoryId,
      int installmentCount,
      List<CreditCardInstallment> installments,
      Instant createdAt,
      CreditCardPurchaseStatus status
  ) {
    validateId(id);
    validateCardId(cardId);
    validateDescription(description);
    validateTotalAmount(totalAmount);
    validatePurchaseDate(purchaseDate);
    validateInstallmentCount(installmentCount);
    validateCreatedAt(createdAt);
    validateStatus(status);

    if (installments == null || installments.size() != installmentCount) {
      throw new IllegalArgumentException(
          "Installment count must match the installment list"
      );
    }

    Money sum = Money.zero(totalAmount.currency());

    for (CreditCardInstallment installment : installments) {
      if (installment == null) {
        throw new IllegalArgumentException(
            "Installments cannot contain null values"
        );
      }

      sum = sum.add(installment.getAmount());
    }

    if (!sum.equals(totalAmount)) {
      throw new IllegalArgumentException(
          "Installments must sum to the purchase total"
      );
    }

    return new CreditCardPurchase(
        id,
        cardId,
        description.trim(),
        totalAmount,
        purchaseDate,
        categoryId,
        installmentCount,
        List.copyOf(installments),
        createdAt,
        status
    );
  }

  /**
   * Cancels the purchase.
   */
  public void cancel() {
    if (status == CreditCardPurchaseStatus.CANCELLED) {
      throw new IllegalStateException("Purchase is already cancelled");
    }

    status = CreditCardPurchaseStatus.CANCELLED;
  }

  public UUID getId() {
    return id;
  }

  public UUID getCardId() {
    return cardId;
  }

  public String getDescription() {
    return description;
  }

  public Money getTotalAmount() {
    return totalAmount;
  }

  public LocalDate getPurchaseDate() {
    return purchaseDate;
  }

  public UUID getCategoryId() {
    return categoryId;
  }

  public int getInstallmentCount() {
    return installmentCount;
  }

  public List<CreditCardInstallment> getInstallments() {
    return installments;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public CreditCardPurchaseStatus getStatus() {
    return status;
  }

  public boolean isCancelled() {
    return status == CreditCardPurchaseStatus.CANCELLED;
  }

  private static List<Money> splitAmount(
      Money totalAmount,
      int installmentCount
  ) {
    long totalCents = totalAmount.amount()
        .movePointRight(2)
        .longValueExact();

    if (installmentCount > totalCents) {
      throw new IllegalArgumentException(
          "Installment count cannot exceed the amount in cents"
      );
    }

    long baseCents = totalCents / installmentCount;
    long remainder = totalCents % installmentCount;
    List<Money> amounts = new ArrayList<>(installmentCount);

    for (int index = 0; index < installmentCount; index++) {
      long cents = baseCents + (index < remainder ? 1 : 0);
      amounts.add(Money.of(
          BigDecimal.valueOf(cents, 2),
          totalAmount.currency()
      ));
    }

    return amounts;
  }

  private static void validateId(UUID id) {
    if (id == null) {
      throw new IllegalArgumentException("Purchase ID cannot be null");
    }
  }

  private static void validateCardId(UUID cardId) {
    if (cardId == null) {
      throw new IllegalArgumentException(
          "Purchase card ID cannot be null"
      );
    }
  }

  private static void validateDescription(String description) {
    if (description == null || description.isBlank()) {
      throw new IllegalArgumentException(
          "Purchase description cannot be null or blank"
      );
    }
  }

  private static void validateTotalAmount(Money totalAmount) {
    if (totalAmount == null) {
      throw new IllegalArgumentException(
          "Purchase total cannot be null"
      );
    }

    if (totalAmount.isZero() || totalAmount.isNegative()) {
      throw new IllegalArgumentException(
          "Purchase total must be greater than zero"
      );
    }
  }

  private static void validatePurchaseDate(LocalDate purchaseDate) {
    if (purchaseDate == null) {
      throw new IllegalArgumentException(
          "Purchase date cannot be null"
      );
    }
  }

  private static void validateInstallmentCount(int installmentCount) {
    if (installmentCount < 1) {
      throw new IllegalArgumentException(
          "Installment count must be at least 1"
      );
    }
  }

  private static void validateInvoiceIds(
      List<UUID> invoiceIds,
      int installmentCount
  ) {
    if (invoiceIds == null || invoiceIds.size() != installmentCount) {
      throw new IllegalArgumentException(
          "Invoice identifiers must match the installment count"
      );
    }

    if (invoiceIds.stream().anyMatch(id -> id == null)) {
      throw new IllegalArgumentException(
          "Invoice identifiers cannot contain null values"
      );
    }
  }

  private static void validateCreatedAt(Instant createdAt) {
    if (createdAt == null) {
      throw new IllegalArgumentException(
          "Purchase creation instant cannot be null"
      );
    }
  }

  private static void validateStatus(CreditCardPurchaseStatus status) {
    if (status == null) {
      throw new IllegalArgumentException(
          "Purchase status cannot be null"
      );
    }
  }
}
