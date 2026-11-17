package com.pedro.ledger.infrastructure.persistence.creditcard;

import com.pedro.ledger.domain.creditcard.CreditCardInvoice;
import com.pedro.ledger.domain.creditcard.CreditCardInvoiceRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistence adapter for credit card invoices.
 */
@Repository
public class CreditCardInvoicePersistenceRepository
    implements CreditCardInvoiceRepository {

  private final CreditCardInvoiceJpaRepository jpaRepository;
  private final CreditCardInvoiceMapper mapper;

  public CreditCardInvoicePersistenceRepository(
      CreditCardInvoiceJpaRepository jpaRepository,
      CreditCardInvoiceMapper mapper
  ) {
    this.jpaRepository = jpaRepository;
    this.mapper = mapper;
  }

  @Override
  public CreditCardInvoice save(CreditCardInvoice invoice) {
    return mapper.toDomain(
        jpaRepository.save(mapper.toEntity(invoice))
    );
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<CreditCardInvoice> findById(UUID id) {
    return jpaRepository.findById(id).map(mapper::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<CreditCardInvoice> findByIdForUpdate(UUID id) {
    return jpaRepository.findByIdForUpdate(id).map(mapper::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<CreditCardInvoice> findByCardIdAndClosingDate(
      UUID cardId,
      LocalDate closingDate
  ) {
    return jpaRepository.findByCard_IdAndClosingDate(cardId, closingDate)
        .map(mapper::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public List<CreditCardInvoice> findByCardId(UUID cardId) {
    return jpaRepository.findByCard_IdOrderByClosingDateDesc(cardId)
        .stream()
        .map(mapper::toDomain)
        .toList();
  }
}
