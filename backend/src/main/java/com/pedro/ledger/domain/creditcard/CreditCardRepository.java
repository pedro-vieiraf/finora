package com.pedro.ledger.domain.creditcard;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Defines persistence operations for credit cards.
 */
public interface CreditCardRepository {

  CreditCard save(CreditCard card);

  Optional<CreditCard> findById(UUID id);

  /**
   * Finds a card and locks it for update.
   *
   * @param id card identifier
   * @return the locked card, if found
   */
  Optional<CreditCard> findByIdForUpdate(UUID id);

  List<CreditCard> findAll();
}
