package com.pedro.ledger.infrastructure.web.openfinance;

import com.pedro.ledger.infrastructure.openfinance.ConnectedCardBills;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class ConnectedCardBillsController {

  private final ConnectedCardBills bills;

  public ConnectedCardBillsController(ConnectedCardBills bills) {
    this.bills = bills;
  }

  @GetMapping("/credit-cards/open-finance/bills")
  public List<ConnectedCardBills.Card> findAll() {
    try {
      return bills.findAll();
    } catch (RuntimeException exception) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
          "Faturas do Open Finance indisponíveis no momento.");
    }
  }
}
