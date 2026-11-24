package com.pedro.ledger.infrastructure.web.openfinance;

import com.pedro.ledger.infrastructure.openfinance.OpenFinanceImportStore;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OpenFinancePendingDebitsController {

  private final OpenFinanceImportStore store;

  public OpenFinancePendingDebitsController(OpenFinanceImportStore store) {
    this.store = store;
  }

  @GetMapping("/transactions/open-finance/pending-debits")
  public List<OpenFinanceImportStore.PendingDebit> findAll() {
    return store.pendingDebits();
  }
}
