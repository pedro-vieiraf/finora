package com.pedro.ledger.infrastructure.persistence.creditcard;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * JPA repository for credit card invoice payments.
 */
public interface CreditCardInvoicePaymentJpaRepository
    extends JpaRepository<CreditCardInvoicePaymentEntity, UUID> {

  List<CreditCardInvoicePaymentEntity> findByInvoice_IdOrderByPaidAtDesc(
      UUID invoiceId
  );
}
