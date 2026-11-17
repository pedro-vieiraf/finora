package com.pedro.ledger.infrastructure.persistence.creditcard;

import com.pedro.ledger.domain.creditcard.CreditCardPurchaseStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

/**
 * JPA entity representing a credit card purchase.
 */
@Entity
@Table(name = "credit_card_purchases")
public class CreditCardPurchaseEntity {

  @Id
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "card_id", nullable = false)
  private CreditCardEntity card;

  @Column(nullable = false)
  private String description;

  @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
  private BigDecimal totalAmount;

  @Column(nullable = false, length = 3)
  private String currency;

  @Column(name = "purchase_date", nullable = false)
  private LocalDate purchaseDate;

  @Column(name = "category_id")
  private UUID categoryId;

  @Column(name = "installment_count", nullable = false)
  private int installmentCount;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private CreditCardPurchaseStatus status;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @OneToMany(
      mappedBy = "purchase",
      cascade = CascadeType.ALL,
      fetch = FetchType.EAGER
  )
  @OrderBy("installmentNumber ASC")
  private List<CreditCardInstallmentEntity> installments = new ArrayList<>();

  protected CreditCardPurchaseEntity() {
  }

  public CreditCardPurchaseEntity(
      UUID id,
      CreditCardEntity card,
      String description,
      BigDecimal totalAmount,
      Currency currency,
      LocalDate purchaseDate,
      UUID categoryId,
      int installmentCount,
      CreditCardPurchaseStatus status,
      Instant createdAt
  ) {
    this.id = id;
    this.card = card;
    this.description = description;
    this.totalAmount = totalAmount;
    this.currency = currency.getCurrencyCode();
    this.purchaseDate = purchaseDate;
    this.categoryId = categoryId;
    this.installmentCount = installmentCount;
    this.status = status;
    this.createdAt = createdAt;
  }

  public void addInstallment(CreditCardInstallmentEntity installment) {
    installments.add(installment);
  }

  public UUID getId() {
    return id;
  }

  public CreditCardEntity getCard() {
    return card;
  }

  public UUID getCardId() {
    return card.getId();
  }

  public String getDescription() {
    return description;
  }

  public BigDecimal getTotalAmount() {
    return totalAmount;
  }

  public Currency getCurrency() {
    return Currency.getInstance(currency);
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

  public CreditCardPurchaseStatus getStatus() {
    return status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public List<CreditCardInstallmentEntity> getInstallments() {
    return installments;
  }
}
