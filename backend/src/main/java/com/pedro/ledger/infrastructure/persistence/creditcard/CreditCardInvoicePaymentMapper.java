package com.pedro.ledger.infrastructure.persistence.creditcard;

import com.pedro.ledger.domain.creditcard.CreditCardInvoicePayment;
import com.pedro.ledger.domain.money.Money;
import com.pedro.ledger.infrastructure.persistence.account.AccountEntity;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

/**
 * Maps invoice payments between domain and persistence layers.
 */
@Component
public class CreditCardInvoicePaymentMapper {

  private final EntityManager entityManager;

  public CreditCardInvoicePaymentMapper(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  public CreditCardInvoicePaymentEntity toEntity(
      CreditCardInvoicePayment payment
  ) {
    CreditCardInvoiceEntity invoice = entityManager.getReference(
        CreditCardInvoiceEntity.class,
        payment.getInvoiceId()
    );

    AccountEntity account = entityManager.getReference(
        AccountEntity.class,
        payment.getAccountId()
    );

    return new CreditCardInvoicePaymentEntity(
        payment.getId(),
        invoice,
        account,
        payment.getAmount().amount(),
        payment.getAmount().currency(),
        payment.getPaidAt()
    );
  }

  public CreditCardInvoicePayment toDomain(
      CreditCardInvoicePaymentEntity entity
  ) {
    return CreditCardInvoicePayment.create(
        entity.getId(),
        entity.getInvoiceId(),
        entity.getAccountId(),
        Money.of(entity.getAmount(), entity.getCurrency()),
        entity.getPaidAt()
    );
  }
}
