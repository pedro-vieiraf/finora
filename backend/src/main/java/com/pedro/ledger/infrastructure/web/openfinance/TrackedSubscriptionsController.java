package com.pedro.ledger.infrastructure.web.openfinance;

import com.pedro.ledger.infrastructure.openfinance.TrackedSubscriptions;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/open-finance/subscriptions")
public class TrackedSubscriptionsController {

  private final TrackedSubscriptions subscriptions;

  public TrackedSubscriptionsController(TrackedSubscriptions subscriptions) {
    this.subscriptions = subscriptions;
  }

  @GetMapping
  public List<TrackedSubscriptions.Subscription> list() {
    return subscriptions.findAll();
  }

  @PutMapping("/bank/{transactionId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void trackBank(@PathVariable UUID transactionId) {
    subscriptions.trackBank(transactionId);
  }

  @PutMapping("/cards/{cardId}/charges/{chargeId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void trackCard(@PathVariable UUID cardId, @PathVariable String chargeId) {
    subscriptions.trackCard(cardId, chargeId);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void remove(@PathVariable UUID id) {
    subscriptions.remove(id);
  }
}
