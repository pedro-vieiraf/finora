package com.pedro.ledger.infrastructure.persistence.creditcard;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.UUID;

/**
 * JPA entity representing a credit card purchase installment.
 */
@Entity
@Table(
    name = "credit_card_installments",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_credit_card_installments_purchase_number",
        columnNames = {"purchase_id", "installment_number"}
    )
)
public class CreditCardInstallmentEntity {

  @Id
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "purchase_id", nullable = false)
  private CreditCardPurchaseEntity purchase;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "invoice_id", nullable = false)
  private CreditCardInvoiceEntity invoice;

  @Column(name = "installment_number", nullable = false)
  private int installmentNumber;

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal amount;

  @Column(nullable = false, length = 3)
  private String currency;

  protected CreditCardInstallmentEntity() {
  }

  public CreditCardInstallmentEntity(
      UUID id,
      CreditCardPurchaseEntity purchase,
      CreditCardInvoiceEntity invoice,
      int installmentNumber,
      BigDecimal amount,
      Currency currency
  ) {
    this.id = id;
    this.purchase = purchase;
    this.invoice = invoice;
    this.installmentNumber = installmentNumber;
    this.amount = amount;
    this.currency = currency.getCurrencyCode();
  }

  public UUID getId() {
    return id;
  }

  public CreditCardPurchaseEntity getPurchase() {
    return purchase;
  }

  public CreditCardInvoiceEntity getInvoice() {
    return invoice;
  }

  public UUID getInvoiceId() {
    return invoice.getId();
  }

  public int getInstallmentNumber() {
    return installmentNumber;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public Currency getCurrency() {
    return Currency.getInstance(currency);
  }
}
