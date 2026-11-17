package com.pedro.ledger.infrastructure.persistence.creditcard;

import com.pedro.ledger.domain.creditcard.CreditCardInstallment;
import com.pedro.ledger.domain.creditcard.CreditCardPurchase;
import com.pedro.ledger.domain.money.Money;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Maps credit card purchases between domain and persistence layers.
 */
@Component
public class CreditCardPurchaseMapper {

  private final EntityManager entityManager;

  public CreditCardPurchaseMapper(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  public CreditCardPurchaseEntity toEntity(CreditCardPurchase purchase) {
    CreditCardEntity card = entityManager.getReference(
        CreditCardEntity.class,
        purchase.getCardId()
    );

    CreditCardPurchaseEntity entity = new CreditCardPurchaseEntity(
        purchase.getId(),
        card,
        purchase.getDescription(),
        purchase.getTotalAmount().amount(),
        purchase.getTotalAmount().currency(),
        purchase.getPurchaseDate(),
        purchase.getCategoryId(),
        purchase.getInstallmentCount(),
        purchase.getStatus(),
        purchase.getCreatedAt()
    );

    for (CreditCardInstallment installment : purchase.getInstallments()) {
      CreditCardInvoiceEntity invoice = entityManager.getReference(
          CreditCardInvoiceEntity.class,
          installment.getInvoiceId()
      );

      entity.addInstallment(new CreditCardInstallmentEntity(
          installment.getId(),
          entity,
          invoice,
          installment.getNumber(),
          installment.getAmount().amount(),
          installment.getAmount().currency()
      ));
    }

    return entity;
  }

  public CreditCardPurchase toDomain(CreditCardPurchaseEntity entity) {
    List<CreditCardInstallment> installments = entity.getInstallments()
        .stream()
        .map(installment -> new CreditCardInstallment(
            installment.getId(),
            installment.getInstallmentNumber(),
            Money.of(installment.getAmount(), installment.getCurrency()),
            installment.getInvoiceId()
        ))
        .toList();

    return CreditCardPurchase.restore(
        entity.getId(),
        entity.getCardId(),
        entity.getDescription(),
        Money.of(entity.getTotalAmount(), entity.getCurrency()),
        entity.getPurchaseDate(),
        entity.getCategoryId(),
        entity.getInstallmentCount(),
        installments,
        entity.getCreatedAt(),
        entity.getStatus()
    );
  }
}
