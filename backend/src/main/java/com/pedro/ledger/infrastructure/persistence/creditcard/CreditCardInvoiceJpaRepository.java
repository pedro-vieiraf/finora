package com.pedro.ledger.infrastructure.persistence.creditcard;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * JPA repository for credit card invoices.
 */
public interface CreditCardInvoiceJpaRepository
    extends JpaRepository<CreditCardInvoiceEntity, UUID> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("""
      select invoice
      from CreditCardInvoiceEntity invoice
      where invoice.id = :id
      """)
  Optional<CreditCardInvoiceEntity> findByIdForUpdate(
      @Param("id") UUID id
  );

  Optional<CreditCardInvoiceEntity> findByCard_IdAndClosingDate(
      UUID cardId,
      LocalDate closingDate
  );

  List<CreditCardInvoiceEntity> findByCard_IdOrderByClosingDateDesc(
      UUID cardId
  );
}
