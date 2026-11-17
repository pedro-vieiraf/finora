package com.pedro.ledger.infrastructure.persistence.creditcard;

import com.pedro.ledger.domain.creditcard.CreditCardPurchase;
import com.pedro.ledger.domain.creditcard.CreditCardPurchaseRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistence adapter for credit card purchases.
 */
@Repository
public class CreditCardPurchasePersistenceRepository
    implements CreditCardPurchaseRepository {

  private final CreditCardPurchaseJpaRepository jpaRepository;
  private final CreditCardPurchaseMapper mapper;

  public CreditCardPurchasePersistenceRepository(
      CreditCardPurchaseJpaRepository jpaRepository,
      CreditCardPurchaseMapper mapper
  ) {
    this.jpaRepository = jpaRepository;
    this.mapper = mapper;
  }

  @Override
  public CreditCardPurchase save(CreditCardPurchase purchase) {
    return mapper.toDomain(
        jpaRepository.save(mapper.toEntity(purchase))
    );
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<CreditCardPurchase> findById(UUID id) {
    return jpaRepository.findById(id).map(mapper::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public List<CreditCardPurchase> findByCardId(UUID cardId) {
    return jpaRepository.findByCard_IdOrderByPurchaseDateDesc(cardId)
        .stream()
        .map(mapper::toDomain)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<CreditCardPurchase> findByInvoiceId(UUID invoiceId) {
    return jpaRepository.findByInvoiceId(invoiceId)
        .stream()
        .map(mapper::toDomain)
        .toList();
  }
}
