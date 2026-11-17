package com.pedro.ledger.infrastructure.web.creditcard;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request for paying a credit card invoice.
 */
public record CreateCreditCardPaymentRequest(
    UUID id,

    @NotNull
    UUID accountId,

    @NotNull
    @DecimalMin(value = "0.01")
    BigDecimal amount
) {
}
