package com.pedro.ledger.infrastructure.web.creditcard;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request for creating a credit card purchase.
 */
public record CreateCreditCardPurchaseRequest(
    UUID id,

    @NotBlank
    String description,

    @NotNull
    @DecimalMin(value = "0.01")
    BigDecimal amount,

    @NotNull
    LocalDate purchaseDate,

    UUID categoryId,

    @Min(1)
    int installmentCount
) {
}
