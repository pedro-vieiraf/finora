package com.pedro.ledger.infrastructure.web.creditcard;

import com.pedro.ledger.application.creditcard.CreditCardApplicationService;
import com.pedro.ledger.domain.creditcard.CreditCard;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for credit card endpoints.
 */
@RestController
@RequestMapping("/credit-cards")
public class CreditCardController {

  private final CreditCardApplicationService service;

  public CreditCardController(CreditCardApplicationService service) {
    this.service = service;
  }

  @PostMapping
  public ResponseEntity<CreditCardResponse> create(
      @Valid @RequestBody CreateCreditCardRequest request
  ) {
    CreditCard card = service.create(
        request.name(),
        request.issuer(),
        request.creditLimit(),
        request.closingDay(),
        request.dueDay(),
        request.currency()
    );

    return ResponseEntity.status(HttpStatus.CREATED)
        .body(CreditCardResponse.from(
            card,
            service.committedAmount(card)
        ));
  }

  @GetMapping
  public ResponseEntity<List<CreditCardResponse>> findAll() {
    List<CreditCardResponse> cards = service.findAll()
        .stream()
        .map(card -> CreditCardResponse.from(
            card,
            service.committedAmount(card)
        ))
        .toList();

    return ResponseEntity.ok(cards);
  }

  @GetMapping("/{id}")
  public ResponseEntity<CreditCardResponse> findById(
      @PathVariable UUID id
  ) {
    return service.findById(id)
        .map(card -> CreditCardResponse.from(
            card,
            service.committedAmount(card)
        ))
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @PatchMapping("/{id}")
  public ResponseEntity<CreditCardResponse> update(
      @PathVariable UUID id,
      @Valid @RequestBody UpdateCreditCardRequest request
  ) {
    CreditCard card = service.update(
        id,
        request.name(),
        request.issuer(),
        request.creditLimit(),
        request.closingDay(),
        request.dueDay()
    );

    return ResponseEntity.ok(CreditCardResponse.from(
        card,
        service.committedAmount(card)
    ));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
    service.deactivate(id);

    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/{id}/permanent")
  public ResponseEntity<Void> deletePermanently(@PathVariable UUID id) {
    service.delete(id);

    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/activation")
  public ResponseEntity<CreditCardResponse> activate(
      @PathVariable UUID id
  ) {
    CreditCard card = service.activate(id);

    return ResponseEntity.ok(CreditCardResponse.from(
        card,
        service.committedAmount(card)
    ));
  }
}
