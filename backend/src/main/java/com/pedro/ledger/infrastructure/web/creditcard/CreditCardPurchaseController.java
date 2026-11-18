package com.pedro.ledger.infrastructure.web.creditcard;

import com.pedro.ledger.application.creditcard.CreditCardPurchaseApplicationService;
import com.pedro.ledger.domain.creditcard.CreditCardPurchase;
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
 * REST controller for credit card purchase endpoints.
 */
@RestController
@RequestMapping("/credit-cards/{cardId}/purchases")
public class CreditCardPurchaseController {

  private final CreditCardPurchaseApplicationService service;

  public CreditCardPurchaseController(
      CreditCardPurchaseApplicationService service
  ) {
    this.service = service;
  }

  @PostMapping
  public ResponseEntity<CreditCardPurchaseResponse> create(
      @PathVariable UUID cardId,
      @Valid @RequestBody CreateCreditCardPurchaseRequest request
  ) {
    CreditCardPurchase purchase = service.create(
        cardId,
        request.id(),
        request.amount(),
        request.description(),
        request.purchaseDate(),
        request.categoryId(),
        request.installmentCount()
    );

    return ResponseEntity.status(HttpStatus.CREATED)
        .body(CreditCardPurchaseResponse.from(purchase));
  }

  @GetMapping
  public ResponseEntity<List<CreditCardPurchaseResponse>> findAll(
      @PathVariable UUID cardId
  ) {
    List<CreditCardPurchaseResponse> purchases = service
        .findByCardId(cardId)
        .stream()
        .map(CreditCardPurchaseResponse::from)
        .toList();

    return ResponseEntity.ok(purchases);
  }

  @GetMapping("/{purchaseId}")
  public ResponseEntity<CreditCardPurchaseResponse> findById(
      @PathVariable UUID cardId,
      @PathVariable UUID purchaseId
  ) {
    return service.findById(purchaseId)
        .filter(purchase -> purchase.getCardId().equals(cardId))
        .map(CreditCardPurchaseResponse::from)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @PostMapping("/{purchaseId}/cancellation")
  public ResponseEntity<CreditCardPurchaseResponse> cancel(
      @PathVariable UUID cardId,
      @PathVariable UUID purchaseId
  ) {
    CreditCardPurchase purchase = service.cancel(cardId, purchaseId);

    return ResponseEntity.ok(CreditCardPurchaseResponse.from(purchase));
  }
}
