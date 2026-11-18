package com.pedro.ledger.infrastructure.persistence.creditcard;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * JPA repository for credit cards.
 */
public interface CreditCardJpaRepository
    extends JpaRepository<CreditCardEntity, UUID> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select card from CreditCardEntity card where card.id = :id")
  Optional<CreditCardEntity> findByIdForUpdate(@Param("id") UUID id);
}
