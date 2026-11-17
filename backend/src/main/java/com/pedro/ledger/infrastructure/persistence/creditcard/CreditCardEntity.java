package com.pedro.ledger.infrastructure.persistence.creditcard;

import com.pedro.ledger.domain.creditcard.CreditCardStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.UUID;

/**
 * JPA entity representing a credit card.
 */
@Entity
@Table(name = "credit_cards")
public class CreditCardEntity {

  @Id
  private UUID id;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false)
  private String issuer;

  @Column(name = "credit_limit", nullable = false, precision = 19, scale = 2)
  private BigDecimal creditLimit;

  @Column(name = "closing_day", nullable = false)
  private int closingDay;

  @Column(name = "due_day", nullable = false)
  private int dueDay;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private CreditCardStatus status;

  @Column(nullable = false, length = 3)
  private String currency;

  protected CreditCardEntity() {
  }

  public CreditCardEntity(
      UUID id,
      String name,
      String issuer,
      BigDecimal creditLimit,
      int closingDay,
      int dueDay,
      CreditCardStatus status,
      Currency currency
  ) {
    this.id = id;
    this.name = name;
    this.issuer = issuer;
    this.creditLimit = creditLimit;
    this.closingDay = closingDay;
    this.dueDay = dueDay;
    this.status = status;
    this.currency = currency.getCurrencyCode();
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

  public BigDecimal getCreditLimit() {
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
    return Currency.getInstance(currency);
  }
}
