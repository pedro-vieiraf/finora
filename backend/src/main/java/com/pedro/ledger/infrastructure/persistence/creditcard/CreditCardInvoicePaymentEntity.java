package com.pedro.ledger.infrastructure.persistence.creditcard;

import com.pedro.ledger.infrastructure.persistence.account.AccountEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

/**
 * JPA entity representing a payment made against a credit card invoice.
 */
@Entity
@Table(name = "credit_card_invoice_payments")
public class CreditCardInvoicePaymentEntity {

  @Id
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "invoice_id", nullable = false)
  private CreditCardInvoiceEntity invoice;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "account_id", nullable = false)
  private AccountEntity account;

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal amount;

  @Column(nullable = false, length = 3)
  private String currency;

  @Column(name = "paid_at", nullable = false)
  private Instant paidAt;

  protected CreditCardInvoicePaymentEntity() {
  }

  public CreditCardInvoicePaymentEntity(
      UUID id,
      CreditCardInvoiceEntity invoice,
      AccountEntity account,
      BigDecimal amount,
      Currency currency,
      Instant paidAt
  ) {
    this.id = id;
    this.invoice = invoice;
    this.account = account;
    this.amount = amount;
    this.currency = currency.getCurrencyCode();
    this.paidAt = paidAt;
  }

  public UUID getId() {
    return id;
  }

  public CreditCardInvoiceEntity getInvoice() {
    return invoice;
  }

  public UUID getInvoiceId() {
    return invoice.getId();
  }

  public AccountEntity getAccount() {
    return account;
  }

  public UUID getAccountId() {
    return account.getId();
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public Currency getCurrency() {
    return Currency.getInstance(currency);
  }

  public Instant getPaidAt() {
    return paidAt;
  }
}
