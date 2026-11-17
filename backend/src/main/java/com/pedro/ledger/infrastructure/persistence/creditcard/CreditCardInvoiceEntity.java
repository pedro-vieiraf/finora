package com.pedro.ledger.infrastructure.persistence.creditcard;

import com.pedro.ledger.domain.creditcard.CreditCardInvoiceStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.UUID;

/**
 * JPA entity representing a credit card invoice.
 */
@Entity
@Table(
    name = "credit_card_invoices",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_credit_card_invoices_card_closing",
        columnNames = {"card_id", "closing_date"}
    )
)
public class CreditCardInvoiceEntity {

  @Id
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "card_id", nullable = false)
  private CreditCardEntity card;

  @Column(name = "cycle_start", nullable = false)
  private LocalDate cycleStart;

  @Column(name = "cycle_end", nullable = false)
  private LocalDate cycleEnd;

  @Column(name = "closing_date", nullable = false)
  private LocalDate closingDate;

  @Column(name = "due_date", nullable = false)
  private LocalDate dueDate;

  @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
  private BigDecimal total;

  @Column(name = "paid_amount", nullable = false, precision = 19, scale = 2)
  private BigDecimal paid;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private CreditCardInvoiceStatus status;

  @Column(nullable = false, length = 3)
  private String currency;

  protected CreditCardInvoiceEntity() {
  }

  public CreditCardInvoiceEntity(
      UUID id,
      CreditCardEntity card,
      LocalDate cycleStart,
      LocalDate cycleEnd,
      LocalDate closingDate,
      LocalDate dueDate,
      BigDecimal total,
      BigDecimal paid,
      CreditCardInvoiceStatus status,
      Currency currency
  ) {
    this.id = id;
    this.card = card;
    this.cycleStart = cycleStart;
    this.cycleEnd = cycleEnd;
    this.closingDate = closingDate;
    this.dueDate = dueDate;
    this.total = total;
    this.paid = paid;
    this.status = status;
    this.currency = currency.getCurrencyCode();
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

  public BigDecimal getTotal() {
    return total;
  }

  public BigDecimal getPaid() {
    return paid;
  }

  public CreditCardInvoiceStatus getStatus() {
    return status;
  }

  public Currency getCurrency() {
    return Currency.getInstance(currency);
  }
}
