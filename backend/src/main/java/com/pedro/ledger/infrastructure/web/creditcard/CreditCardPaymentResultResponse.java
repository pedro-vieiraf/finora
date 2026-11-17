package com.pedro.ledger.infrastructure.web.creditcard;

import com.pedro.ledger.domain.creditcard.CreditCardInvoice;
import com.pedro.ledger.domain.creditcard.CreditCardInvoicePayment;

/**
 * Response for an invoice payment including the updated invoice.
 */
public record CreditCardPaymentResultResponse(
    CreditCardPaymentResponse payment,
    CreditCardInvoiceResponse invoice
) {

  public static CreditCardPaymentResultResponse from(
      CreditCardInvoicePayment payment,
      CreditCardInvoice invoice
  ) {
    return new CreditCardPaymentResultResponse(
        CreditCardPaymentResponse.from(payment),
        CreditCardInvoiceResponse.from(invoice)
    );
  }
}
