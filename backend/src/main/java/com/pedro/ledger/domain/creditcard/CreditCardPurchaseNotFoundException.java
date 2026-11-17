package com.pedro.ledger.domain.creditcard;

/**
 * Thrown when a credit card purchase cannot be found.
 */
public class CreditCardPurchaseNotFoundException extends RuntimeException {

  public CreditCardPurchaseNotFoundException() {
    super("Credit card purchase not found");
  }
}
