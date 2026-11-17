package com.pedro.ledger.infrastructure.persistence.creditcard;

import com.pedro.ledger.domain.creditcard.CreditCardInvoicePayment;
import com.pedro.ledger.domain.creditcard.CreditCardInvoicePaymentRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistence adapter for credit card invoice payments.
 */
@Repository
public class CreditCardInvoicePaymentPersistenceRepository
    implements CreditCardInvoicePaymentRepository {

  private final CreditCardInvoicePaymentJpaRepository jpaRepository;
  private final CreditCardInvoicePaymentMapper mapper;

  public CreditCardInvoicePaymentPersistenceRepository(
      CreditCardInvoicePaymentJpaRepository jpaRepository,
      CreditCardInvoicePaymentMapper mapper
  ) {
    this.jpaRepository = jpaRepository;
    this.mapper = mapper;
  }

  @Override
  public CreditCardInvoicePayment save(
      CreditCardInvoicePayment payment
  ) {
    return mapper.toDomain(
        jpaRepository.save(mapper.toEntity(payment))
    );
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<CreditCardInvoicePayment> findById(UUID id) {
    return jpaRepository.findById(id).map(mapper::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public List<CreditCardInvoicePayment> findByInvoiceId(UUID invoiceId) {
    return jpaRepository.findByInvoice_IdOrderByPaidAtDesc(invoiceId)
        .stream()
        .map(mapper::toDomain)
        .toList();
  }
}
