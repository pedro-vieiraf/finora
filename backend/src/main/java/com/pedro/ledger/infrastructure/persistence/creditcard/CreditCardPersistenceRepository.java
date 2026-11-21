package com.pedro.ledger.infrastructure.persistence.creditcard;

import com.pedro.ledger.domain.creditcard.CreditCard;
import com.pedro.ledger.domain.creditcard.CreditCardRepository;
import com.pedro.ledger.domain.creditcard.CreditCardStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistence adapter for credit cards.
 */
@Repository
public class CreditCardPersistenceRepository
    implements CreditCardRepository {

  private final CreditCardJpaRepository jpaRepository;
  private final CreditCardMapper mapper;

  public CreditCardPersistenceRepository(
      CreditCardJpaRepository jpaRepository,
      CreditCardMapper mapper
  ) {
    this.jpaRepository = jpaRepository;
    this.mapper = mapper;
  }

  @Override
  public CreditCard save(CreditCard card) {
    return mapper.toDomain(jpaRepository.save(mapper.toEntity(card)));
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<CreditCard> findById(UUID id) {
    return jpaRepository.findById(id).map(mapper::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<CreditCard> findByIdForUpdate(UUID id) {
    return jpaRepository.findByIdForUpdate(id).map(mapper::toDomain);
  }

  /**
   * Finds all credit cards.
   *
   * <p>Deleted cards are kept so historical invoices and purchases can
   * reference them, but they are hidden from listings.
   *
   * @return all non-deleted credit cards
   */
  @Override
  @Transactional(readOnly = true)
  public List<CreditCard> findAll() {
    return jpaRepository.findAllByStatusNot(CreditCardStatus.DELETED)
        .stream()
        .map(mapper::toDomain)
        .toList();
  }
}
