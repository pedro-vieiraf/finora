package com.pedro.ledger.infrastructure.web.creditcard;

import com.pedro.ledger.domain.creditcard.CreditCardInvoicePayment;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Response representing an invoice payment.
 */
public record CreditCardPaymentResponse(
    UUID id,
    UUID invoiceId,
    UUID accountId,
    BigDecimal amount,
    String currency,
    Instant paidAt
) {

  public static CreditCardPaymentResponse from(
      CreditCardInvoicePayment payment
  ) {
    return new CreditCardPaymentResponse(
        payment.getId(),
        payment.getInvoiceId(),
        payment.getAccountId(),
        payment.getAmount().amount(),
        payment.getAmount().currency().getCurrencyCode(),
        payment.getPaidAt()
    );
  }
}
