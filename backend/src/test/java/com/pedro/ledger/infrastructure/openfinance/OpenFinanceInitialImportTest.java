package com.pedro.ledger.infrastructure.openfinance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pedro.ledger.domain.account.Account;
import com.pedro.ledger.domain.account.AccountRepository;
import com.pedro.ledger.domain.account.AccountSource;
import com.pedro.ledger.domain.account.AccountType;
import com.pedro.ledger.domain.money.Money;
import com.pedro.ledger.domain.transaction.Transaction;
import com.pedro.ledger.domain.transaction.TransactionRepository;
import com.pedro.ledger.domain.transaction.TransactionSource;
import com.pedro.ledger.domain.transaction.TransactionType;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class OpenFinanceInitialImportTest {

  private static final UUID ITEM = UUID.randomUUID();
  private static final UUID ACCOUNT = UUID.randomUUID();
  private static final LocalDate FROM = LocalDate.parse("2026-10-01");
  private static final LocalDate TO = LocalDate.parse("2026-11-22");
  private static final OpenFinanceInitialImport.Selection SELECTION =
      new OpenFinanceInitialImport.Selection("Banco", ITEM, ACCOUNT);

  @Test
  void parsesTheAmountReviewAsAReadOnlyCommand() {
    var command = PluggyImportCommand.parse(new String[]{
        "review", "2026-10-31", "2026-11-04", "11.34", "Banco", ITEM.toString(), ACCOUNT.toString()
    });

    assertThat(command.confirm()).isFalse();
    assertThat(command.reviewAmount()).isEqualByComparingTo("11.34");
    assertThat(command.selections()).containsExactly(SELECTION);
  }

  @Test
  void readsAllPagesWithOnlyPostedTransactionsAndDeduplicatesOverlappingPages() {
    var client = mock(PluggyClient.class);
    var first = new PluggyClient.TransactionPage(List.of(
        transaction("expense", "-10.20", "DEBIT", "POSTED"),
        transaction("pending", "-1.00", "DEBIT", "PENDING")
    ), "?after=abc");
    when(client.listBankAccounts(ITEM)).thenReturn(List.of(bank()));
    when(client.listTransactions(ACCOUNT, FROM, TO)).thenReturn(first);
    when(client.nextPage(first)).thenReturn(new PluggyClient.TransactionPage(List.of(
        transaction("expense", "-10.20", "DEBIT", "POSTED"),
        transaction("income", "25.01", "CREDIT", "POSTED")
    ), null));

    var result = new OpenFinanceInitialImport(client).prepare(SELECTION, FROM, TO);

    assertThat(result.balance()).isEqualTo(Money.of("100.00"));
    assertThat(result.postings()).extracting(OpenFinanceInitialImport.Posting::type)
        .containsExactly(TransactionType.EXPENSE, TransactionType.INCOME);
    assertThat(result.postings().getFirst().amount()).isEqualTo(Money.of("10.20"));
  }

  @Test
  void detectsRepeatedPaginationCursor() {
    var client = mock(PluggyClient.class);
    var page = new PluggyClient.TransactionPage(List.of(), "?after=abc");
    when(client.listBankAccounts(ITEM)).thenReturn(List.of(bank()));
    when(client.listTransactions(ACCOUNT, FROM, TO)).thenReturn(page);
    when(client.nextPage(page)).thenReturn(page);

    assertThatThrownBy(() -> new OpenFinanceInitialImport(client).prepare(SELECTION, FROM, TO))
        .isInstanceOf(IllegalStateException.class).hasMessageContaining("cursor");
  }

  @Test
  void flagsPotentialInternalTransfersAcrossTheSelectedAccounts() {
    var date = Instant.parse("2026-10-01T12:00:00Z");
    var amount = Money.of("10.00");
    var debit = new OpenFinanceInitialImport.Posting(
        "a", TransactionType.EXPENSE, amount, null, date);
    var credit = new OpenFinanceInitialImport.Posting(
        "b", TransactionType.INCOME, amount, null, date);
    var other = new OpenFinanceInitialImport.Selection("Outro banco", UUID.randomUUID(), UUID.randomUUID());
    var first = new OpenFinanceInitialImport.AccountImport(SELECTION, AccountType.CHECKING,
        Money.of("100.00"), FROM, List.of(debit));
    var second = new OpenFinanceInitialImport.AccountImport(other, AccountType.CHECKING,
        Money.of("100.00"), FROM, List.of(credit));

    assertThat(OpenFinanceInitialImport.possibleInternalTransfers(List.of(first, second)))
        .containsExactly(new OpenFinanceInitialImport.TransferCandidate(
            new OpenFinanceInitialImport.AccountPosting(SELECTION, debit),
            new OpenFinanceInitialImport.AccountPosting(other, credit)));
    assertThat(OpenFinanceInitialImport.possibleInternalTransfers(List.of(first))).isEmpty();

    var candidates = OpenFinanceInitialImport.possibleInternalTransfers(List.of(first, second));
    assertThat(OpenFinanceTransferReview.resolve(candidates, List.of(
        OpenFinanceTransferReview.Decision.parse("--transfer=Banco,Outro banco,2026-10-01,10.00")
    ))).containsExactlyElementsOf(candidates);
    assertThat(OpenFinanceTransferReview.resolve(candidates, List.of(
        OpenFinanceTransferReview.Decision.parse("--separate=Banco,Outro banco,2026-10-01,10.00")
    ))).isEmpty();
    assertThatThrownBy(() -> OpenFinanceTransferReview.resolve(candidates, List.of()))
        .isInstanceOf(IllegalStateException.class).hasMessageContaining("Revise todos");
  }

  @Test
  void recordsTheAuthoritativeBankBalanceAndImportedHistoryWithoutReapplyingIt() {
    var accounts = mock(AccountRepository.class);
    var transactions = mock(TransactionRepository.class);
    var jdbc = mock(JdbcTemplate.class);
    var entities = mock(EntityManager.class);
    when(accounts.findAll()).thenReturn(List.of());
    when(transactions.save(any(Transaction.class))).thenAnswer(call -> call.getArgument(0));
    when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any(), any(UUID.class)))
        .thenReturn(List.of());

    var importData = new OpenFinanceInitialImport.AccountImport(SELECTION, AccountType.CHECKING,
        Money.of("100.00"), FROM, List.of(new OpenFinanceInitialImport.Posting(
            "external", TransactionType.EXPENSE, Money.of("10.00"), "Compra",
            Instant.parse("2026-10-01T12:00:00Z"))));
    var result = new OpenFinanceImportStore(accounts, transactions, jdbc, entities)
        .initialize(List.of(importData), List.of());

    var account = ArgumentCaptor.forClass(Account.class);
    var transaction = ArgumentCaptor.forClass(Transaction.class);
    verify(accounts).save(account.capture());
    verify(transactions).save(transaction.capture());
    assertThat(account.getValue().getSource()).isEqualTo(AccountSource.OPEN_FINANCE);
    assertThat(account.getValue().getBalance()).isEqualTo(Money.of("100.00"));
    assertThat(transaction.getValue().getSource()).isEqualTo(TransactionSource.OPEN_FINANCE);
    assertThat(transaction.getValue().getAccountId()).isEqualTo(account.getValue().getId());
    assertThat(result).isEqualTo(new OpenFinanceImportStore.ImportResult(1, 1));
  }

  @Test
  void recordsBothProviderPostingsAsOneTransferWithoutReapplyingTheBalances() {
    var accounts = mock(AccountRepository.class);
    var transactions = mock(TransactionRepository.class);
    var jdbc = mock(JdbcTemplate.class);
    var entities = mock(EntityManager.class);
    when(accounts.findAll()).thenReturn(List.of());
    when(transactions.save(any(Transaction.class))).thenAnswer(call -> call.getArgument(0));

    var other = new OpenFinanceInitialImport.Selection("Outro", UUID.randomUUID(), UUID.randomUUID());
    var amount = Money.of("11.34");
    var date = Instant.parse("2026-10-31T20:14:46Z");
    var debit = new OpenFinanceInitialImport.Posting("debit", TransactionType.EXPENSE, amount, null, date);
    var credit = new OpenFinanceInitialImport.Posting("credit", TransactionType.INCOME, amount, null, date.plusSeconds(1));
    var imports = List.of(
        new OpenFinanceInitialImport.AccountImport(SELECTION, AccountType.CHECKING,
            Money.of("5.00"), FROM, List.of(debit)),
        new OpenFinanceInitialImport.AccountImport(other, AccountType.CHECKING,
            Money.of("100.00"), FROM, List.of(credit))
    );
    var transfer = new OpenFinanceInitialImport.TransferCandidate(
        new OpenFinanceInitialImport.AccountPosting(SELECTION, debit),
        new OpenFinanceInitialImport.AccountPosting(other, credit)
    );

    var result = new OpenFinanceImportStore(accounts, transactions, jdbc, entities)
        .initialize(imports, List.of(transfer));

    var account = ArgumentCaptor.forClass(Account.class);
    var transaction = ArgumentCaptor.forClass(Transaction.class);
    verify(accounts, org.mockito.Mockito.times(2)).save(account.capture());
    verify(transactions).save(transaction.capture());
    assertThat(account.getAllValues()).extracting(Account::getBalance)
        .containsExactly(Money.of("5.00"), Money.of("100.00"));
    var saved = transaction.getValue();
    assertThat(saved.getType()).isEqualTo(TransactionType.TRANSFER);
    assertThat(saved.getTimestamp()).isEqualTo(date);
    assertThat(saved.getAccountId()).isEqualTo(account.getAllValues().get(0).getId());
    assertThat(saved.getDestinationAccountId()).isEqualTo(account.getAllValues().get(1).getId());
    verify(jdbc).update(anyString(), org.mockito.ArgumentMatchers.eq(saved.getId()),
        org.mockito.ArgumentMatchers.eq(saved.getAccountId()), org.mockito.ArgumentMatchers.eq("debit"));
    verify(jdbc).update(anyString(), org.mockito.ArgumentMatchers.eq(saved.getId()),
        org.mockito.ArgumentMatchers.eq(saved.getDestinationAccountId()), org.mockito.ArgumentMatchers.eq("credit"));
    assertThat(result).isEqualTo(new OpenFinanceImportStore.ImportResult(2, 1));
  }

  private static PluggyClient.BankAccount bank() {
    return new PluggyClient.BankAccount(ACCOUNT, ITEM, "Banco", "CHECKING_ACCOUNT",
        "BRL", new BigDecimal("100.00"));
  }

  private static PluggyClient.Transaction transaction(
      String id, String amount, String direction, String status
  ) {
    return new PluggyClient.Transaction(id, ACCOUNT, "Banco",
        Instant.parse("2026-10-01T12:00:00Z"), "BRL", new BigDecimal(amount), null, direction, status);
  }
}
