package com.pedro.ledger.domain.creditcard;

/**
 * Thrown when an operation requires an active credit card.
 */
public class CreditCardInactiveException extends RuntimeException {

  public CreditCardInactiveException(String message) {
    super(message);
  }
}
