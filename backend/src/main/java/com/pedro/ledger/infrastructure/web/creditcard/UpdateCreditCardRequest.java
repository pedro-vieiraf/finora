package com.pedro.ledger.infrastructure.web.creditcard;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;

/**
 * Request for updating credit card data.
 */
public record UpdateCreditCardRequest(
    String name,
    String issuer,

    @DecimalMin(value = "0.01")
    BigDecimal creditLimit,

    @Min(1)
    @Max(31)
    Integer closingDay,

    @Min(1)
    @Max(31)
    Integer dueDay
) {
}
