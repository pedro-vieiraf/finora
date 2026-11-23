package com.pedro.ledger.infrastructure.openfinance;

import static org.assertj.core.api.Assertions.assertThat;

import com.pedro.ledger.domain.account.AccountType;
import com.pedro.ledger.domain.money.Money;
import com.pedro.ledger.domain.transaction.TransactionType;
import com.pedro.ledger.domain.account.Account;
import com.pedro.ledger.domain.account.AccountRepository;
import com.pedro.ledger.domain.account.AccountSource;
import com.pedro.ledger.domain.account.AccountStatus;
import com.pedro.ledger.domain.transaction.TransactionRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpenFinanceSyncPlanTest {

  @Test
  void importsOnlyNewUnambiguousPostingsAndLeavesPossibleTransfersPending() {
    var first = new OpenFinanceInitialImport.Selection("Primeira", UUID.randomUUID(), UUID.randomUUID());
    var second = new OpenFinanceInitialImport.Selection("Segunda", UUID.randomUUID(), UUID.randomUUID());
    var snapshots = List.of(
        snapshot(first, posting("old", TransactionType.EXPENSE, "20.00"),
            posting("debit", TransactionType.EXPENSE, "11.34"),
            posting("purchase", TransactionType.EXPENSE, "42.00")),
        snapshot(second, posting("credit", TransactionType.INCOME, "11.34"))
    );
    var imported = Set.of(new OpenFinanceSyncPlan.ExternalPosting(first.accountId(), "old"));

    var plan = OpenFinanceSyncPlan.prepare(snapshots, imported);

    assertThat(plan.pendingPairs()).isEqualTo(1);
    assertThat(plan.newPostings()).extracting(entry -> entry.posting().externalId())
        .containsExactly("purchase");
  }

  @Test
  void doesNotResurfacePairsThatWereAlreadyReviewedAndImported() {
    var first = new OpenFinanceInitialImport.Selection("Primeira", UUID.randomUUID(), UUID.randomUUID());
    var second = new OpenFinanceInitialImport.Selection("Segunda", UUID.randomUUID(), UUID.randomUUID());
    var snapshots = List.of(
        snapshot(first, posting("debit", TransactionType.EXPENSE, "11.34")),
        snapshot(second, posting("credit", TransactionType.INCOME, "11.34"))
    );
    var imported = Set.of(new OpenFinanceSyncPlan.ExternalPosting(first.accountId(), "debit"),
        new OpenFinanceSyncPlan.ExternalPosting(second.accountId(), "credit"));

    var plan = OpenFinanceSyncPlan.prepare(snapshots, imported);

    assertThat(plan.pendingPairs()).isZero();
    assertThat(plan.newPostings()).isEmpty();
  }

  @Test
  void synchronizesAnActiveLinkWithTheBankSnapshotAndSavesTheNewPosting() {
    var accounts = mock(AccountRepository.class);
    var transactions = mock(TransactionRepository.class);
    var jdbc = mock(JdbcTemplate.class);
    var localId = UUID.randomUUID();
    var selection = new OpenFinanceInitialImport.Selection("Banco", UUID.randomUUID(), UUID.randomUUID());
    var bank = snapshot(selection, posting("external", TransactionType.EXPENSE, "10.00"));
    var startingBalance = Money.of("120.00");
    var account = Account.restore(localId, "Banco", AccountType.CHECKING, AccountStatus.ACTIVE,
        startingBalance, AccountSource.OPEN_FINANCE);
    var store = spy(new OpenFinanceImportStore(accounts, transactions, jdbc, mock(EntityManager.class)));
    doReturn(List.of(new OpenFinanceImportStore.LinkedBank(localId, selection, bank.from())))
        .when(store).linkedBanks();
    when(jdbc.queryForObject(anyString(), eq(Boolean.class))).thenReturn(true);
    when(jdbc.update(anyString(), any(java.sql.Timestamp.class), eq(localId), any(java.sql.Timestamp.class)))
        .thenReturn(1);
    when(jdbc.query(anyString(),
        org.mockito.ArgumentMatchers.<RowMapper<OpenFinanceSyncPlan.ExternalPosting>>any(), eq(localId)))
        .thenReturn(List.of());
    when(accounts.findByIdForUpdate(localId)).thenReturn(java.util.Optional.of(account));
    when(transactions.save(any())).thenAnswer(call -> call.getArgument(0));

    var result = store.synchronize(List.of(bank), Instant.parse("2026-11-22T13:00:00Z"));

    var saved = ArgumentCaptor.forClass(Account.class);
    verify(accounts).save(saved.capture());
    assertThat(saved.getValue().getBalance()).isEqualTo(bank.balance());
    assertThat(account.getBalance()).isEqualTo(startingBalance);
    assertThat(result).isEqualTo(new OpenFinanceImportStore.SyncResult(1, 0));
  }

  private static OpenFinanceInitialImport.AccountImport snapshot(
      OpenFinanceInitialImport.Selection account, OpenFinanceInitialImport.Posting... entries
  ) {
    return new OpenFinanceInitialImport.AccountImport(account, AccountType.CHECKING, Money.of("100.00"),
        LocalDate.parse("2026-10-01"), List.of(entries));
  }

  private static OpenFinanceInitialImport.Posting posting(String id, TransactionType type, String amount) {
    return new OpenFinanceInitialImport.Posting(id, type, Money.of(amount), "Banco",
        Instant.parse("2026-11-22T12:00:00Z"));
  }
}
