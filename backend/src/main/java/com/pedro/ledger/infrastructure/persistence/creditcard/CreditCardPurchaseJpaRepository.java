package com.pedro.ledger.infrastructure.persistence.creditcard;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * JPA repository for credit card purchases.
 */
public interface CreditCardPurchaseJpaRepository
    extends JpaRepository<CreditCardPurchaseEntity, UUID> {

  List<CreditCardPurchaseEntity> findByCard_IdOrderByPurchaseDateDesc(
      UUID cardId
  );

  @Query("""
      select distinct purchase
      from CreditCardPurchaseEntity purchase
      join purchase.installments installment
      where installment.invoice.id = :invoiceId
      order by purchase.purchaseDate desc
      """)
  List<CreditCardPurchaseEntity> findByInvoiceId(
      @Param("invoiceId") UUID invoiceId
  );
}
