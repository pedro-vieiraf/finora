package com.pedro.ledger.infrastructure.web.creditcard;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Request for creating a credit card.
 */
public record CreateCreditCardRequest(
    @NotBlank
    String name,

    @NotBlank
    String issuer,

    @NotNull
    @DecimalMin(value = "0.01")
    BigDecimal creditLimit,

    @NotNull
    @Min(1)
    @Max(31)
    Integer closingDay,

    @NotNull
    @Min(1)
    @Max(31)
    Integer dueDay,

    String currency
) {
}
