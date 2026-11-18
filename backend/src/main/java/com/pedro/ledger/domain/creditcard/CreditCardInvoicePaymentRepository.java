package com.pedro.ledger.domain.creditcard;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Defines persistence operations for credit card invoice payments.
 */
public interface CreditCardInvoicePaymentRepository {

  CreditCardInvoicePayment save(CreditCardInvoicePayment payment);

  Optional<CreditCardInvoicePayment> findById(UUID id);

  List<CreditCardInvoicePayment> findByInvoiceId(UUID invoiceId);
}
