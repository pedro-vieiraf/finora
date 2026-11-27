package com.pedro.ledger.infrastructure.web.transaction;

import com.pedro.ledger.application.transaction.TransactionApplicationService;
import com.pedro.ledger.domain.transaction.TransactionSource;
import com.pedro.ledger.domain.transaction.TransactionType;
import com.pedro.ledger.infrastructure.openfinance.OpenFinanceImportStore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TransactionStatementController {

  private final TransactionApplicationService transactions;
  private final OpenFinanceImportStore openFinance;

  public TransactionStatementController(TransactionApplicationService transactions,
      OpenFinanceImportStore openFinance) {
    this.transactions = transactions;
    this.openFinance = openFinance;
  }

  @PutMapping("/open-finance/transfers")
  public TransactionResponse reconcile(@Valid @RequestBody TransferRequest request) {
    return TransactionResponse.from(openFinance.reconcileTransfer(request.debitId(), request.creditId()));
  }

  public record TransferRequest(@NotNull UUID debitId, @NotNull UUID creditId) {}

  @GetMapping("/open-finance/statement")
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public List<TransactionResponse> statement() {
    var confirmed = transactions.findAll().stream().map(TransactionResponse::from);
    var pending = openFinance.pendingDebits().stream().map(debit -> new TransactionResponse(
        debit.statementId(), debit.amount(), debit.currency(), TransactionType.EXPENSE,
        debit.description(), debit.timestamp(), TransactionSource.OPEN_FINANCE,
        debit.accountId(), null, null, null, null, null, true));
    return Stream.concat(confirmed, pending)
        .sorted(Comparator.comparing(TransactionResponse::timestamp).reversed()
            .thenComparing(TransactionResponse::id))
        .toList();
  }
}
