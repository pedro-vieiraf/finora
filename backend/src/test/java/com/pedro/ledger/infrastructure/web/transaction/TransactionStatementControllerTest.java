package com.pedro.ledger.infrastructure.web.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.pedro.ledger.application.transaction.TransactionApplicationService;
import com.pedro.ledger.domain.money.Money;
import com.pedro.ledger.domain.transaction.Transaction;
import com.pedro.ledger.domain.transaction.TransactionSource;
import com.pedro.ledger.domain.transaction.TransactionType;
import com.pedro.ledger.infrastructure.openfinance.OpenFinanceImportStore;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TransactionStatementControllerTest {
  @Test
  void mergesPendingDebitsWithConfirmedTransactionsInDateOrder() {
    var app = mock(TransactionApplicationService.class);
    var store = mock(OpenFinanceImportStore.class);
    var account = UUID.randomUUID();
    var day = Instant.parse("2026-11-26T10:00:00Z");
    var expense = Transaction.create(Money.of("2.00"), TransactionType.EXPENSE, "Pix", day,
        TransactionSource.OPEN_FINANCE, account, null, null);
    var debit = new OpenFinanceImportStore.PendingDebit(account, "debit", new BigDecimal("12.30"),
        "BRL", "Compra", day.plusSeconds(1));
    when(app.findAll()).thenReturn(List.of(expense));
    when(store.pendingDebits()).thenReturn(List.of(debit));

    var rows = new TransactionStatementController(app, store).statement();

    assertThat(rows).extracting(TransactionResponse::id).containsExactly(debit.statementId(), expense.getId());
    assertThat(rows).extracting(TransactionResponse::type).containsOnly(TransactionType.EXPENSE);
    assertThat(rows).extracting(TransactionResponse::pending).containsExactly(true, false);
  }
}
