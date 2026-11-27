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
import com.pedro.ledger.domain.transaction.Transaction;
import com.pedro.ledger.domain.transaction.TransactionSource;
import com.pedro.ledger.domain.transaction.TransactionStatus;
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
import static org.mockito.Mockito.verifyNoInteractions;

class OpenFinanceSyncPlanTest {

  @Test
  void importsAllNewPostingsEvenWhenTheirAmountsSuggestATransfer() {
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
        .containsExactly("debit", "purchase", "credit");
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
    var bank = snapshot(selection, posting("external", TransactionType.EXPENSE, "10.00"),
        posting("corrected", TransactionType.EXPENSE, "11.55"));
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
        .thenReturn(List.of(new OpenFinanceSyncPlan.ExternalPosting(selection.accountId(), "corrected")));
    when(accounts.findByIdForUpdate(localId)).thenReturn(java.util.Optional.of(account));
    when(transactions.save(any())).thenAnswer(call -> call.getArgument(0));

    var result = store.synchronize(List.of(bank), Instant.parse("2026-11-22T13:00:00Z"));

    var saved = ArgumentCaptor.forClass(Account.class);
    verify(accounts).save(saved.capture());
    assertThat(saved.getValue().getBalance()).isEqualTo(bank.balance());
    assertThat(account.getBalance()).isEqualTo(startingBalance);
    var savedTransaction = ArgumentCaptor.forClass(com.pedro.ledger.domain.transaction.Transaction.class);
    verify(transactions).save(savedTransaction.capture());
    var previousPending = new OpenFinanceImportStore.PendingDebit(localId, "external",
        new java.math.BigDecimal("10.00"), "BRL", null, Instant.parse("2026-11-22T12:00:00Z"));
    assertThat(savedTransaction.getValue().getId()).isEqualTo(previousPending.statementId());
    verify(jdbc).batchUpdate(org.mockito.ArgumentMatchers.contains("IS DISTINCT FROM"),
        org.mockito.ArgumentMatchers.<List<Object[]>>argThat(rows -> rows.size() == 1
            && new java.math.BigDecimal("11.55").equals(rows.getFirst()[0])
            && "corrected".equals(rows.getFirst()[5])));
    assertThat(result).isEqualTo(new OpenFinanceImportStore.SyncResult(1, 0));
  }

  @Test
  void reconcilesTheTwoProviderLinksWithoutChangingEitherBalance() {
    var accountRepository = mock(AccountRepository.class);
    var transactions = mock(TransactionRepository.class);
    var jdbc = mock(JdbcTemplate.class);
    var debit = Transaction.create(Money.of("20.00"), TransactionType.EXPENSE, "Pix enviado",
        Instant.parse("2026-11-26T12:00:00Z"), TransactionSource.OPEN_FINANCE, UUID.randomUUID(), null, null);
    var credit = Transaction.create(Money.of("20.00"), TransactionType.INCOME, "Pix recebido",
        Instant.parse("2026-11-26T12:01:00Z"), TransactionSource.OPEN_FINANCE, UUID.randomUUID(), null, null);
    when(jdbc.queryForObject(anyString(), eq(Boolean.class))).thenReturn(true);
    when(transactions.findByIdForUpdate(debit.getId())).thenReturn(java.util.Optional.of(debit));
    when(transactions.findByIdForUpdate(credit.getId())).thenReturn(java.util.Optional.of(credit));
    when(transactions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(jdbc.update(anyString(), any(UUID.class), eq(debit.getId()), eq(debit.getAccountId()),
        eq(credit.getId()), eq(credit.getAccountId()))).thenReturn(2);
    var store = new OpenFinanceImportStore(accountRepository, transactions, jdbc, mock(EntityManager.class));

    var transfer = store.reconcileTransfer(debit.getId(), credit.getId());

    assertThat(transfer.getType()).isEqualTo(TransactionType.TRANSFER);
    assertThat(transfer.getAccountId()).isEqualTo(debit.getAccountId());
    assertThat(transfer.getDestinationAccountId()).isEqualTo(credit.getAccountId());
    assertThat(debit.getStatus()).isEqualTo(TransactionStatus.REVERSED);
    assertThat(credit.getStatus()).isEqualTo(TransactionStatus.REVERSED);
    verifyNoInteractions(accountRepository);
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
