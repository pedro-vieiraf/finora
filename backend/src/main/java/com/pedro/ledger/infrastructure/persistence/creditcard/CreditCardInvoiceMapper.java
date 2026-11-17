package com.pedro.ledger.infrastructure.persistence.creditcard;

import com.pedro.ledger.domain.creditcard.CreditCardInvoice;
import com.pedro.ledger.domain.money.Money;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

/**
 * Maps credit card invoices between domain and persistence layers.
 */
@Component
public class CreditCardInvoiceMapper {

  private final EntityManager entityManager;

  public CreditCardInvoiceMapper(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  public CreditCardInvoiceEntity toEntity(CreditCardInvoice invoice) {
    CreditCardEntity card = entityManager.getReference(
        CreditCardEntity.class,
        invoice.getCardId()
    );

    return new CreditCardInvoiceEntity(
        invoice.getId(),
        card,
        invoice.getCycleStart(),
        invoice.getCycleEnd(),
        invoice.getClosingDate(),
        invoice.getDueDate(),
        invoice.getTotal().amount(),
        invoice.getPaid().amount(),
        invoice.getStatus(),
        invoice.getCurrency()
    );
  }

  public CreditCardInvoice toDomain(CreditCardInvoiceEntity entity) {
    return CreditCardInvoice.restore(
        entity.getId(),
        entity.getCardId(),
        entity.getCycleStart(),
        entity.getCycleEnd(),
        entity.getClosingDate(),
        entity.getDueDate(),
        entity.getCurrency(),
        Money.of(entity.getTotal(), entity.getCurrency()),
        Money.of(entity.getPaid(), entity.getCurrency()),
        entity.getStatus()
    );
  }
}
