package com.pedro.ledger.infrastructure.web.creditcard;

import com.pedro.ledger.domain.creditcard.CreditCardInvoice;
import com.pedro.ledger.domain.creditcard.CreditCardPurchase;
import java.util.List;

/**
 * Detailed invoice response including the purchases in the invoice.
 */
public record CreditCardInvoiceDetailsResponse(
    CreditCardInvoiceResponse invoice,
    List<CreditCardPurchaseResponse> purchases
) {

  public static CreditCardInvoiceDetailsResponse from(
      CreditCardInvoice invoice,
      List<CreditCardPurchase> purchases
  ) {
    return new CreditCardInvoiceDetailsResponse(
        CreditCardInvoiceResponse.from(invoice),
        purchases.stream()
            .map(CreditCardPurchaseResponse::from)
            .toList()
    );
  }
}
