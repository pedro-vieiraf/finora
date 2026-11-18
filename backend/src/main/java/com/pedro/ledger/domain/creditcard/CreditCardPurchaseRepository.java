package com.pedro.ledger.domain.creditcard;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Defines persistence operations for credit card purchases.
 */
public interface CreditCardPurchaseRepository {

  CreditCardPurchase save(CreditCardPurchase purchase);

  Optional<CreditCardPurchase> findById(UUID id);

  List<CreditCardPurchase> findByCardId(UUID cardId);

  List<CreditCardPurchase> findByInvoiceId(UUID invoiceId);
}
