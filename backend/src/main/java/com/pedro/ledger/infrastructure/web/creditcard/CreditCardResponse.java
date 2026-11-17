package com.pedro.ledger.infrastructure.web.creditcard;

import com.pedro.ledger.domain.creditcard.CreditCard;
import com.pedro.ledger.domain.creditcard.CreditCardStatus;
import com.pedro.ledger.domain.money.Money;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Response representing a credit card and its available limit.
 */
public record CreditCardResponse(
    UUID id,
    String name,
    String issuer,
    BigDecimal creditLimit,
    BigDecimal availableLimit,
    int closingDay,
    int dueDay,
    CreditCardStatus status,
    String currency
) {

  public static CreditCardResponse from(
      CreditCard card,
      Money committedAmount
  ) {
    return new CreditCardResponse(
        card.getId(),
        card.getName(),
        card.getIssuer(),
        card.getCreditLimit().amount(),
        card.availableLimit(committedAmount).amount(),
        card.getClosingDay(),
        card.getDueDay(),
        card.getStatus(),
        card.getCurrency().getCurrencyCode()
    );
  }
}
