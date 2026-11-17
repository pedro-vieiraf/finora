package com.pedro.ledger.infrastructure.persistence.creditcard;

import com.pedro.ledger.domain.creditcard.CreditCard;
import com.pedro.ledger.domain.creditcard.CreditCardRepository;
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

  @Override
  @Transactional(readOnly = true)
  public List<CreditCard> findAll() {
    return jpaRepository.findAll()
        .stream()
        .map(mapper::toDomain)
        .toList();
  }
}
