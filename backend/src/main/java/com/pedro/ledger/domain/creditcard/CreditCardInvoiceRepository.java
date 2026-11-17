package com.pedro.ledger.domain.creditcard;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Defines persistence operations for credit card invoices.
 */
public interface CreditCardInvoiceRepository {

  CreditCardInvoice save(CreditCardInvoice invoice);

  Optional<CreditCardInvoice> findById(UUID id);

  /**
   * Finds an invoice and locks it for update.
   *
   * @param id invoice identifier
   * @return the locked invoice, if found
   */
  Optional<CreditCardInvoice> findByIdForUpdate(UUID id);

  Optional<CreditCardInvoice> findByCardIdAndClosingDate(
      UUID cardId,
      LocalDate closingDate
  );

  List<CreditCardInvoice> findByCardId(UUID cardId);
}
