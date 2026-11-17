package com.pedro.ledger.domain.creditcard;

/**
 * Thrown when a credit card invoice cannot be found.
 */
public class CreditCardInvoiceNotFoundException extends RuntimeException {

  public CreditCardInvoiceNotFoundException() {
    super("Credit card invoice not found");
  }
}
