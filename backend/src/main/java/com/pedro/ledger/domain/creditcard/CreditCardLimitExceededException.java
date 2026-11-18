package com.pedro.ledger.domain.creditcard;

/**
 * Thrown when a purchase would exceed the available credit limit.
 */
public class CreditCardLimitExceededException extends IllegalStateException {

  public CreditCardLimitExceededException(String message) {
    super(message);
  }
}
