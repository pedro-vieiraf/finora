package com.pedro.ledger.infrastructure.web.creditcard;

import com.pedro.ledger.domain.creditcard.CreditCardInstallment;
import com.pedro.ledger.domain.creditcard.CreditCardPurchase;
import com.pedro.ledger.domain.creditcard.CreditCardPurchaseStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Response representing a credit card purchase and its installments.
 */
public record CreditCardPurchaseResponse(
    UUID id,
    UUID cardId,
    String description,
    BigDecimal totalAmount,
    String currency,
    LocalDate purchaseDate,
    UUID categoryId,
    int installmentCount,
    CreditCardPurchaseStatus status,
    Instant createdAt,
    List<InstallmentResponse> installments
) {

  public static CreditCardPurchaseResponse from(
      CreditCardPurchase purchase
  ) {
    return new CreditCardPurchaseResponse(
        purchase.getId(),
        purchase.getCardId(),
        purchase.getDescription(),
        purchase.getTotalAmount().amount(),
        purchase.getTotalAmount().currency().getCurrencyCode(),
        purchase.getPurchaseDate(),
        purchase.getCategoryId(),
        purchase.getInstallmentCount(),
        purchase.getStatus(),
        purchase.getCreatedAt(),
        purchase.getInstallments()
            .stream()
            .map(InstallmentResponse::from)
            .toList()
    );
  }

  /**
   * Response representing one installment.
   */
  public record InstallmentResponse(
      UUID id,
      int number,
      BigDecimal amount,
      UUID invoiceId
  ) {

    public static InstallmentResponse from(
        CreditCardInstallment installment
    ) {
      return new InstallmentResponse(
          installment.getId(),
          installment.getNumber(),
          installment.getAmount().amount(),
          installment.getInvoiceId()
      );
    }
  }
}
