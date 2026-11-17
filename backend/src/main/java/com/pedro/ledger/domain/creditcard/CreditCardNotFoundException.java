package com.pedro.ledger.domain.creditcard;

/**
 * Thrown when a credit card cannot be found.
 */
public class CreditCardNotFoundException extends RuntimeException {

  public CreditCardNotFoundException() {
    super("Credit card not found");
  }
}
