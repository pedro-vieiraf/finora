package com.pedro.ledger.infrastructure.web.creditcard;

import com.pedro.ledger.application.creditcard.CreditCardInvoiceApplicationService;
import com.pedro.ledger.domain.creditcard.CreditCardInvoice;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for credit card invoice and payment endpoints.
 */
@RestController
@RequestMapping("/credit-cards/{cardId}/invoices")
public class CreditCardInvoiceController {

  private final CreditCardInvoiceApplicationService service;

  public CreditCardInvoiceController(
      CreditCardInvoiceApplicationService service
  ) {
    this.service = service;
  }

  @GetMapping
  public ResponseEntity<List<CreditCardInvoiceResponse>> findAll(
      @PathVariable UUID cardId
  ) {
    List<CreditCardInvoiceResponse> invoices = service
        .findByCardId(cardId)
        .stream()
        .map(CreditCardInvoiceResponse::from)
        .toList();

    return ResponseEntity.ok(invoices);
  }

  @GetMapping("/{invoiceId}")
  public ResponseEntity<CreditCardInvoiceDetailsResponse> findById(
      @PathVariable UUID cardId,
      @PathVariable UUID invoiceId
  ) {
    CreditCardInvoice invoice = service.findById(cardId, invoiceId);

    return ResponseEntity.ok(
        CreditCardInvoiceDetailsResponse.from(
            invoice,
            service.findPurchases(cardId, invoiceId)
        )
    );
  }

  @GetMapping("/{invoiceId}/payments")
  public ResponseEntity<List<CreditCardPaymentResponse>> findPayments(
      @PathVariable UUID cardId,
      @PathVariable UUID invoiceId
  ) {
    List<CreditCardPaymentResponse> payments = service
        .findPayments(cardId, invoiceId)
        .stream()
        .map(CreditCardPaymentResponse::from)
        .toList();

    return ResponseEntity.ok(payments);
  }

  @PostMapping("/{invoiceId}/payments")
  public ResponseEntity<CreditCardPaymentResultResponse> pay(
      @PathVariable UUID cardId,
      @PathVariable UUID invoiceId,
      @Valid @RequestBody CreateCreditCardPaymentRequest request
  ) {
    CreditCardInvoiceApplicationService.PaymentResult result = service.pay(
        cardId,
        invoiceId,
        request.id(),
        request.accountId(),
        request.amount()
    );

    return ResponseEntity.status(HttpStatus.CREATED)
        .body(CreditCardPaymentResultResponse.from(
            result.payment(),
            result.invoice()
        ));
  }
}
