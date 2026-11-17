package com.pedro.ledger.infrastructure.web.creditcard;

import com.pedro.ledger.domain.creditcard.CreditCardInvoice;
import com.pedro.ledger.domain.creditcard.CreditCardInvoiceStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Response representing a credit card invoice.
 */
public record CreditCardInvoiceResponse(
    UUID id,
    UUID cardId,
    LocalDate cycleStart,
    LocalDate cycleEnd,
    LocalDate closingDate,
    LocalDate dueDate,
    BigDecimal totalAmount,
    BigDecimal paidAmount,
    BigDecimal remainingAmount,
    CreditCardInvoiceStatus status,
    String currency
) {

  public static CreditCardInvoiceResponse from(
      CreditCardInvoice invoice
  ) {
    return new CreditCardInvoiceResponse(
        invoice.getId(),
        invoice.getCardId(),
        invoice.getCycleStart(),
        invoice.getCycleEnd(),
        invoice.getClosingDate(),
        invoice.getDueDate(),
        invoice.getTotal().amount(),
        invoice.getPaid().amount(),
        invoice.getRemaining().amount(),
        invoice.getStatus(),
        invoice.getCurrency().getCurrencyCode()
    );
  }
}
