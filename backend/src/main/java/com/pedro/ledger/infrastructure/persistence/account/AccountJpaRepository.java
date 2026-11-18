package com.pedro.ledger.infrastructure.persistence.account;

import com.pedro.ledger.domain.account.AccountStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * JPA Repository.
 */
public interface AccountJpaRepository
    extends JpaRepository<AccountEntity, UUID> {

  List<AccountEntity> findAllByStatusNot(AccountStatus status);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select account from AccountEntity account where account.id = :id")
  Optional<AccountEntity> findByIdForUpdate(@Param("id") UUID id);
}
