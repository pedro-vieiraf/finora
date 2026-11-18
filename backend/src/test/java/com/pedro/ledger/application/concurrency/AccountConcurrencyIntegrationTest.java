package com.pedro.ledger.application.concurrency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pedro.ledger.application.account.AccountApplicationService;
import com.pedro.ledger.application.creditcard.CreditCardApplicationService;
import com.pedro.ledger.application.creditcard.CreditCardInvoiceApplicationService;
import com.pedro.ledger.application.creditcard.CreditCardPurchaseApplicationService;
import com.pedro.ledger.application.recurrence.RecurrenceApplicationService;
import com.pedro.ledger.application.transaction.TransactionApplicationService;
import com.pedro.ledger.domain.account.Account;
import com.pedro.ledger.domain.account.AccountRepository;
import com.pedro.ledger.domain.account.AccountType;
import com.pedro.ledger.domain.creditcard.CreditCard;
import com.pedro.ledger.domain.creditcard.CreditCardInvoice;
import com.pedro.ledger.domain.creditcard.CreditCardPurchase;
import com.pedro.ledger.domain.money.Money;
import com.pedro.ledger.domain.recurrence.Recurrence;
import com.pedro.ledger.domain.recurrence.RecurrenceFrequency;
import com.pedro.ledger.domain.transaction.Transaction;
import com.pedro.ledger.domain.transaction.TransactionRepository;
import com.pedro.ledger.domain.transaction.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@TestPropertySource(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "ledger.recurrence.scheduler-enabled=false"
})
@Testcontainers
@SpringBootTest
class AccountConcurrencyIntegrationTest {

  private static final ZoneId ZONE_ID =
      ZoneId.of("America/Sao_Paulo");

  @Container
  @ServiceConnection
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer("postgres:17");

  @Autowired
  private AccountApplicationService accountService;

  @Autowired
  private AccountRepository accountRepository;

  @Autowired
  private TransactionApplicationService transactionService;

  @Autowired
  private TransactionRepository transactionRepository;

  @Autowired
  private RecurrenceApplicationService recurrenceService;

  @Autowired
  private CreditCardApplicationService cardService;

  @Autowired
  private CreditCardPurchaseApplicationService purchaseService;

  @Autowired
  private CreditCardInvoiceApplicationService invoiceService;

  @Autowired
  private PlatformTransactionManager transactionManager;

  private ExecutorService executor;
  private TransactionTemplate transactionTemplate;

  @BeforeEach
  void setUp() {
    executor = Executors.newFixedThreadPool(4);
    transactionTemplate = new TransactionTemplate(transactionManager);
  }

  @AfterEach
  void tearDown() {
    executor.shutdownNow();
  }

  @Test
  void shouldSerializeInvoicePaymentAndExpenseOnSameAccount() {
    Account account = createAccount("Payment vs expense", "1000.00");
    CreditCard card = createCard();
    CreditCardInvoice invoice = createClosedInvoice(card, "100.00");

    CountDownLatch paymentDone = new CountDownLatch(1);
    CountDownLatch releasePayment = new CountDownLatch(1);

    Future<?> payment = holdTransaction(
        () -> pay(card, invoice, account, "100.00"),
        paymentDone,
        releasePayment
    );

    await(paymentDone);

    CountDownLatch expenseStarted = new CountDownLatch(1);
    Future<?> expense = startAsync(
        expenseStarted,
        () -> createExpense(account, "50.00", "concurrent expense")
    );

    await(expenseStarted);
    assertBlocked(expense);

    releasePayment.countDown();
    awaitFuture(payment);
    awaitFuture(expense);

    assertThat(balance(account)).isEqualTo(new BigDecimal("850.00"));
    assertThat(transactionsOf(account)).hasSize(1);
    assertThat(invoiceService.findById(card.getId(), invoice.getId())
        .getStatus().name()).isEqualTo("PAID");
  }

  @Test
  void shouldSerializeInvoicePaymentAndTransferOnSameAccount() {
    Account source = createAccount("Payment vs transfer source", "1000.00");
    Account destination = createAccount(
        "Payment vs transfer destination",
        "500.00"
    );
    CreditCard card = createCard();
    CreditCardInvoice invoice = createClosedInvoice(card, "100.00");

    CountDownLatch paymentDone = new CountDownLatch(1);
    CountDownLatch releasePayment = new CountDownLatch(1);

    Future<?> payment = holdTransaction(
        () -> pay(card, invoice, source, "100.00"),
        paymentDone,
        releasePayment
    );

    await(paymentDone);

    CountDownLatch transferStarted = new CountDownLatch(1);
    Future<?> transfer = startAsync(
        transferStarted,
        () -> createTransfer(source, destination, "50.00", "concurrent transfer")
    );

    await(transferStarted);
    assertBlocked(transfer);

    releasePayment.countDown();
    awaitFuture(payment);
    awaitFuture(transfer);

    assertThat(balance(source)).isEqualTo(new BigDecimal("850.00"));
    assertThat(balance(destination)).isEqualTo(new BigDecimal("550.00"));
    assertThat(transactionsOf(source)).hasSize(1);
    assertThat(transactionsOf(destination)).hasSize(1);
  }

  @Test
  void shouldSerializeTwoExpensesOnSameAccount() {
    Account account = createAccount("Two expenses", "1000.00");

    CountDownLatch firstDone = new CountDownLatch(1);
    CountDownLatch releaseFirst = new CountDownLatch(1);

    Future<?> first = holdTransaction(
        () -> createExpense(account, "100.00", "first expense"),
        firstDone,
        releaseFirst
    );

    await(firstDone);

    CountDownLatch secondStarted = new CountDownLatch(1);
    Future<?> second = startAsync(
        secondStarted,
        () -> createExpense(account, "100.00", "second expense")
    );

    await(secondStarted);
    assertBlocked(second);

    releaseFirst.countDown();
    awaitFuture(first);
    awaitFuture(second);

    assertThat(balance(account)).isEqualTo(new BigDecimal("800.00"));
    assertThat(transactionsOf(account)).hasSize(2);
  }

  @Test
  void shouldSerializeRecurrenceGenerationAndExpenseOnSameAccount() {
    Account account = createAccount("Recurrence vs expense", "1000.00");
    Recurrence recurrence = recurrenceService.create(
        new BigDecimal("100.00"),
        "BRL",
        TransactionType.EXPENSE,
        "Monthly expense",
        account.getId(),
        null,
        RecurrenceFrequency.MONTHLY,
        LocalDate.of(2026, 5, 10)
    );

    CountDownLatch recurrenceDone = new CountDownLatch(1);
    CountDownLatch releaseRecurrence = new CountDownLatch(1);

    Future<?> recurrenceFuture = holdTransaction(
        () -> recurrenceService.generatePendingOccurrences(
            recurrence.getId(),
            LocalDate.of(2026, 5, 10),
            ZONE_ID
        ),
        recurrenceDone,
        releaseRecurrence
    );

    await(recurrenceDone);

    CountDownLatch expenseStarted = new CountDownLatch(1);
    Future<?> expense = startAsync(
        expenseStarted,
        () -> createExpense(account, "50.00", "expense beside recurrence")
    );

    await(expenseStarted);
    assertBlocked(expense);

    releaseRecurrence.countDown();
    awaitFuture(recurrenceFuture);
    awaitFuture(expense);

    assertThat(balance(account)).isEqualTo(new BigDecimal("850.00"));
    assertThat(transactionsOf(account)).hasSize(2);
  }

  @Test
  void shouldNotDeadlockOnOppositeTransfersBetweenTwoAccounts() {
    Account first = createAccount("Transfer account A", "1000.00");
    Account second = createAccount("Transfer account B", "500.00");
    CyclicBarrier barrier = new CyclicBarrier(2);

    Future<?> firstTransfer = executor.submit(() -> {
      awaitBarrier(barrier);
      createTransfer(first, second, "50.00", "A to B");
      return null;
    });

    Future<?> secondTransfer = executor.submit(() -> {
      awaitBarrier(barrier);
      createTransfer(second, first, "50.00", "B to A");
      return null;
    });

    awaitFuture(firstTransfer);
    awaitFuture(secondTransfer);

    assertThat(balance(first)).isEqualTo(new BigDecimal("1000.00"));
    assertThat(balance(second)).isEqualTo(new BigDecimal("500.00"));
    assertThat(transactionsOf(first)).hasSize(2);
    assertThat(transactionsOf(second)).hasSize(2);
  }

  @Test
  void shouldNotOverwriteBalanceWhenAccountIsRenamedDuringPayment() {
    Account account = createAccount("Payment vs rename", "1000.00");
    CreditCard card = createCard();
    CreditCardInvoice invoice = createClosedInvoice(card, "100.00");

    CountDownLatch paymentDone = new CountDownLatch(1);
    CountDownLatch releasePayment = new CountDownLatch(1);

    Future<?> payment = holdTransaction(
        () -> pay(card, invoice, account, "100.00"),
        paymentDone,
        releasePayment
    );

    await(paymentDone);

    CountDownLatch renameStarted = new CountDownLatch(1);
    Future<?> rename = startAsync(
        renameStarted,
        () -> accountService.update(account.getId(), "Renamed", null)
    );

    await(renameStarted);
    assertBlocked(rename);

    releasePayment.countDown();
    awaitFuture(payment);
    awaitFuture(rename);

    Account updated = accountRepository.findById(account.getId()).orElseThrow();

    assertThat(updated.getBalance()).isEqualTo(Money.of("900.00"));
    assertThat(updated.getName()).isEqualTo("Renamed");
  }

  @Test
  void shouldRollbackTransactionBalanceWhenOperationFails() {
    Account account = createAccount("Rollback transaction", "1000.00");
    String description = "rollback-" + UUID.randomUUID();

    assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
      createExpense(account, "100.00", description);
      throw new IllegalStateException("force rollback");
    })).isInstanceOf(IllegalStateException.class);

    assertThat(balance(account)).isEqualTo(new BigDecimal("1000.00"));
    assertThat(transactionRepository.findAll())
        .noneMatch(transaction -> description.equals(
            transaction.getDescription()
        ));
  }

  @Test
  void shouldRollbackInvoicePaymentAtomicallyWhenOperationFails() {
    Account account = createAccount("Rollback payment", "1000.00");
    CreditCard card = createCard();
    CreditCardInvoice invoice = createClosedInvoice(card, "100.00");

    assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
      pay(card, invoice, account, "100.00");
      throw new IllegalStateException("force rollback");
    })).isInstanceOf(IllegalStateException.class);

    CreditCardInvoice rolledBack = invoiceService.findById(
        card.getId(),
        invoice.getId()
    );

    assertThat(balance(account)).isEqualTo(new BigDecimal("1000.00"));
    assertThat(rolledBack.getPaid()).isEqualTo(Money.zero());
    assertThat(invoiceService.findPayments(card.getId(), invoice.getId()))
        .isEmpty();
  }

  @Test
  void shouldNotReverseTransactionTwiceWhenDeleteIsConcurrent() {
    Account account = createAccount("Double delete", "1000.00");
    Transaction transaction = createExpense(account, "100.00", "delete me");

    CountDownLatch start = new CountDownLatch(1);
    Callable<Boolean> delete = () -> {
      await(start);
      try {
        transactionService.delete(transaction.getId());
        return true;
      } catch (RuntimeException exception) {
        return false;
      }
    };

    Future<Boolean> first = executor.submit(delete);
    Future<Boolean> second = executor.submit(delete);

    start.countDown();

    assertThat(List.of(
        get(first),
        get(second)
    )).containsExactlyInAnyOrder(true, false);

    assertThat(balance(account)).isEqualTo(new BigDecimal("1000.00"));
    assertThat(transactionRepository.findById(transaction.getId()))
        .isEmpty();
  }

  private Account createAccount(String name, String openingBalance) {
    return accountService.create(
        name,
        AccountType.CHECKING,
        new BigDecimal(openingBalance),
        "BRL"
    );
  }

  private CreditCard createCard() {
    return cardService.create(
        "Concurrency Card",
        "Bank",
        new BigDecimal("5000.00"),
        10,
        15,
        "BRL"
    );
  }

  private CreditCardInvoice createClosedInvoice(
      CreditCard card,
      String amount
  ) {
    CreditCardPurchase purchase = purchaseService.create(
        card.getId(),
        null,
        new BigDecimal(amount),
        "Closed invoice purchase",
        LocalDate.of(2020, 5, 10),
        null,
        1
    );

    UUID invoiceId = purchase.getInstallments().get(0).getInvoiceId();

    return invoiceService.findById(card.getId(), invoiceId);
  }

  private void pay(
      CreditCard card,
      CreditCardInvoice invoice,
      Account account,
      String amount
  ) {
    invoiceService.pay(
        card.getId(),
        invoice.getId(),
        null,
        account.getId(),
        new BigDecimal(amount)
    );
  }

  private Transaction createExpense(
      Account account,
      String amount,
      String description
  ) {
    return transactionService.create(
        Money.of(amount),
        TransactionType.EXPENSE,
        description,
        account.getId(),
        null,
        null
    );
  }

  private Transaction createTransfer(
      Account source,
      Account destination,
      String amount,
      String description
  ) {
    return transactionService.create(
        Money.of(amount),
        TransactionType.TRANSFER,
        description,
        source.getId(),
        destination.getId(),
        null
    );
  }

  private BigDecimal balance(Account account) {
    return accountRepository.findById(account.getId())
        .orElseThrow()
        .getBalance()
        .amount();
  }

  private List<Transaction> transactionsOf(Account account) {
    return transactionRepository.findAll()
        .stream()
        .filter(transaction -> account.getId().equals(
            transaction.getAccountId()
        ) || account.getId().equals(
            transaction.getDestinationAccountId()
        ))
        .toList();
  }

  private Future<?> holdTransaction(
      Runnable operation,
      CountDownLatch operationDone,
      CountDownLatch release
  ) {
    return executor.submit(() -> transactionTemplate.executeWithoutResult(
        status -> {
          operation.run();
          operationDone.countDown();
          await(release);
        }
    ));
  }

  private Future<?> startAsync(
      CountDownLatch started,
      Runnable operation
  ) {
    return executor.submit(() -> {
      started.countDown();
      operation.run();
    });
  }

  private void assertBlocked(Future<?> future) {
    assertThatThrownBy(() -> future.get(500, TimeUnit.MILLISECONDS))
        .isInstanceOf(TimeoutException.class);
  }

  private <T> T get(Future<T> future) {
    try {
      return future.get(20, TimeUnit.SECONDS);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new AssertionError(exception);
    } catch (ExecutionException | TimeoutException exception) {
      throw new AssertionError(exception);
    }
  }

  private void awaitFuture(Future<?> future) {
    get(future);
  }

  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(20, TimeUnit.SECONDS)) {
        throw new AssertionError("Timed out waiting for latch");
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new AssertionError(exception);
    }
  }

  private static void awaitBarrier(CyclicBarrier barrier) {
    try {
      barrier.await(20, TimeUnit.SECONDS);
    } catch (Exception exception) {
      throw new AssertionError(exception);
    }
  }
}
