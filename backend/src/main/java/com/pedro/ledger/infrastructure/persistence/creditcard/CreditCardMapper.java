package com.pedro.ledger.infrastructure.persistence.creditcard;

import com.pedro.ledger.domain.creditcard.CreditCard;
import com.pedro.ledger.domain.money.Money;
import org.springframework.stereotype.Component;

/**
 * Maps credit cards between domain and persistence layers.
 */
@Component
public class CreditCardMapper {

  public CreditCardEntity toEntity(CreditCard card) {
    return new CreditCardEntity(
        card.getId(),
        card.getName(),
        card.getIssuer(),
        card.getCreditLimit().amount(),
        card.getClosingDay(),
        card.getDueDay(),
        card.getStatus(),
        card.getCurrency()
    );
  }

  public CreditCard toDomain(CreditCardEntity entity) {
    return CreditCard.restore(
        entity.getId(),
        entity.getName(),
        entity.getIssuer(),
        Money.of(entity.getCreditLimit(), entity.getCurrency()),
        entity.getClosingDay(),
        entity.getDueDay(),
        entity.getStatus()
    );
  }
}
