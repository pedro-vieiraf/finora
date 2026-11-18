package com.pedro.ledger.application.creditcard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pedro.ledger.application.account.AccountApplicationService;
import com.pedro.ledger.domain.account.Account;
import com.pedro.ledger.domain.account.AccountRepository;
import com.pedro.ledger.domain.account.AccountType;
import com.pedro.ledger.domain.creditcard.CreditCard;
import com.pedro.ledger.domain.creditcard.CreditCardInactiveException;
import com.pedro.ledger.domain.creditcard.CreditCardInvoice;
import com.pedro.ledger.domain.creditcard.CreditCardInvoiceRepository;
import com.pedro.ledger.domain.creditcard.CreditCardInvoiceStatus;
import com.pedro.ledger.domain.creditcard.CreditCardLimitExceededException;
import com.pedro.ledger.domain.creditcard.CreditCardPurchase;
import com.pedro.ledger.domain.creditcard.CreditCardPurchaseStatus;
import com.pedro.ledger.domain.money.Money;
import com.pedro.ledger.domain.transaction.TransactionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import jakarta.transaction.Transactional;

@TestPropertySource(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "ledger.recurrence.scheduler-enabled=false"
})
@Testcontainers
@SpringBootTest
@Transactional
@Import(CreditCardIntegrationTest.FixedClockConfig.class)
class CreditCardIntegrationTest {

  private static final Instant FIXED_INSTANT =
      Instant.parse("2026-05-15T12:00:00Z");

  @Container
  @ServiceConnection
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer("postgres:17");

  @TestConfiguration
  static class FixedClockConfig {

    @Bean
    @Primary
    Clock fixedClock() {
      return Clock.fixed(
          FIXED_INSTANT,
          ZoneId.of("America/Sao_Paulo")
      );
    }
  }

  @Autowired
  private AccountApplicationService accountApplicationService;

  @Autowired
  private AccountRepository accountRepository;

  @Autowired
  private CreditCardApplicationService cardService;

  @Autowired
  private CreditCardPurchaseApplicationService purchaseService;

  @Autowired
  private CreditCardInvoiceApplicationService invoiceService;

  @Autowired
  private CreditCardInvoiceRepository invoiceRepository;

  @Autowired
  private TransactionRepository transactionRepository;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  private Account account;
  private CreditCard card;

  @BeforeEach
  void setUp() {
    account = accountApplicationService.create(
        "Checking Account",
        AccountType.CHECKING,
        new BigDecimal("1000.00"),
        "BRL"
    );

    card = cardService.create(
        "Platinum",
        "Bank",
        new BigDecimal("500.00"),
        10,
        15,
        "BRL"
    );
  }

  @Test
  void shouldApplyDatabaseConstraintsAndIndexes() {
    assertThat(jdbcTemplate.queryForObject("""
        select count(*)
        from information_schema.table_constraints
        where table_schema = 'public'
          and table_name = 'credit_cards'
          and constraint_name = 'credit_cards_closing_day_check'
          and constraint_type = 'CHECK'
        """, Integer.class)).isEqualTo(1);

    assertThat(jdbcTemplate.queryForObject("""
        select count(*)
        from information_schema.table_constraints
        where table_schema = 'public'
          and table_name = 'credit_card_invoices'
          and constraint_name = 'credit_card_invoices_amounts_check'
          and constraint_type = 'CHECK'
        """, Integer.class)).isEqualTo(1);

    assertThat(jdbcTemplate.queryForObject("""
        select count(*)
        from pg_indexes
        where schemaname = 'public'
          and indexname = 'uk_credit_card_invoices_card_closing'
        """, Integer.class)).isEqualTo(1);

    assertThat(jdbcTemplate.queryForObject("""
        select count(*)
        from information_schema.table_constraints
        where table_schema = 'public'
          and table_name = 'credit_card_installments'
          and constraint_type = 'FOREIGN KEY'
        """, Integer.class)).isEqualTo(2);

    assertThat(jdbcTemplate.queryForObject("""
        select count(*)
        from pg_indexes
        where schemaname = 'public'
          and indexname in (
            'idx_credit_card_purchases_card_date',
            'idx_credit_card_installments_invoice',
            'idx_credit_card_invoice_payments_invoice'
          )
        """, Integer.class)).isEqualTo(3);
  }

  @Test
  void shouldCreateAndFindCard() {
    CreditCard found = cardService.findById(card.getId()).orElseThrow();

    assertThat(found.getName()).isEqualTo("Platinum");
    assertThat(found.getIssuer()).isEqualTo("Bank");
    assertThat(found.getCreditLimit()).isEqualTo(Money.of("500.00"));
    assertThat(found.getClosingDay()).isEqualTo(10);
    assertThat(found.getDueDay()).isEqualTo(15);
    assertThat(cardService.findAll()).hasSize(1);
  }

  @Test
  void shouldRegisterCashPurchaseWithoutDebitingAccount() {
    CreditCardPurchase purchase = createPurchase(
        "100.00",
        LocalDate.of(2026, 5, 10),
        1
    );

    CreditCardInvoice invoice = invoiceFor(purchase, 0);

    assertThat(purchase.getStatus())
        .isEqualTo(CreditCardPurchaseStatus.ACTIVE);
    assertThat(purchase.getInstallments()).hasSize(1);
    assertThat(invoice.getTotal()).isEqualTo(Money.of("100.00"));
    assertThat(invoice.getStatus())
        .isEqualTo(CreditCardInvoiceStatus.CLOSED);
    assertThat(balance()).isEqualTo(new BigDecimal("1000.00"));
    assertThat(transactionRepository.findAll()).isEmpty();
    assertThat(committed()).isEqualTo(Money.of("100.00"));
    assertThat(available()).isEqualTo(Money.of("400.00"));
  }

  @Test
  void shouldRegisterInstallmentPurchaseAndDistributeCents() {
    CreditCardPurchase purchase = createPurchase(
        "100.00",
        LocalDate.of(2026, 5, 20),
        3
    );

    List<CreditCardInvoice> invoices = List.of(
        invoiceFor(purchase, 0),
        invoiceFor(purchase, 1),
        invoiceFor(purchase, 2)
    );

    assertThat(invoices)
        .extracting(CreditCardInvoice::getTotal)
        .containsExactly(
            Money.of("33.34"),
            Money.of("33.33"),
            Money.of("33.33")
        );

    assertThat(invoices)
        .extracting(CreditCardInvoice::getClosingDate)
        .containsExactly(
            LocalDate.of(2026, 6, 10),
            LocalDate.of(2026, 7, 10),
            LocalDate.of(2026, 8, 10)
        );

    assertThat(invoices)
        .allSatisfy(invoice -> assertThat(invoice.getStatus())
            .isEqualTo(CreditCardInvoiceStatus.OPEN));

    assertThat(balance()).isEqualTo(new BigDecimal("1000.00"));
    assertThat(transactionRepository.findAll()).isEmpty();
    assertThat(committed()).isEqualTo(Money.of("100.00"));
  }

  @Test
  void shouldAssociatePurchasesAroundClosingDay() {
    CreditCardPurchase before = createPurchase(
        "10.00",
        LocalDate.of(2026, 5, 9),
        1
    );
    CreditCardPurchase onClosing = createPurchase(
        "10.00",
        LocalDate.of(2026, 5, 10),
        1
    );
    CreditCardPurchase after = createPurchase(
        "10.00",
        LocalDate.of(2026, 5, 11),
        1
    );

    CreditCardInvoice mayInvoice = invoiceFor(before, 0);
    CreditCardInvoice juneInvoice = invoiceFor(after, 0);

    assertThat(mayInvoice.getClosingDate())
        .isEqualTo(LocalDate.of(2026, 5, 10));
    assertThat(invoiceFor(onClosing, 0).getId())
        .isEqualTo(mayInvoice.getId());
    assertThat(juneInvoice.getClosingDate())
        .isEqualTo(LocalDate.of(2026, 6, 10));
    assertThat(mayInvoice.getTotal()).isEqualTo(Money.of("20.00"));
    assertThat(juneInvoice.getTotal()).isEqualTo(Money.of("10.00"));

    assertThat(invoiceService.findPurchases(
        card.getId(),
        mayInvoice.getId()
    )).hasSize(2);
  }

  @Test
  void shouldPayInvoiceFullyAndRestoreLimit() {
    CreditCardPurchase purchase = createPurchase(
        "100.00",
        LocalDate.of(2026, 5, 10),
        1
    );
    CreditCardInvoice invoice = invoiceFor(purchase, 0);

    CreditCardInvoiceApplicationService.PaymentResult result =
        invoiceService.pay(
            card.getId(),
            invoice.getId(),
            null,
            account.getId(),
            new BigDecimal("100.00")
        );

    assertThat(result.payment().getAmount()).isEqualTo(Money.of("100.00"));
    assertThat(result.invoice().getStatus())
        .isEqualTo(CreditCardInvoiceStatus.PAID);
    assertThat(result.invoice().getPaid()).isEqualTo(Money.of("100.00"));
    assertThat(result.invoice().getRemaining()).isEqualTo(Money.zero());
    assertThat(balance()).isEqualTo(new BigDecimal("900.00"));
    assertThat(transactionRepository.findAll()).isEmpty();
    assertThat(committed()).isEqualTo(Money.zero());
    assertThat(available()).isEqualTo(Money.of("500.00"));
    assertThat(invoiceService.findPayments(
        card.getId(),
        invoice.getId()
    )).hasSize(1);
  }

  @Test
  void shouldPayInvoicePartially() {
    CreditCardPurchase purchase = createPurchase(
        "100.00",
        LocalDate.of(2026, 5, 10),
        1
    );
    CreditCardInvoice invoice = invoiceFor(purchase, 0);

    CreditCardInvoiceApplicationService.PaymentResult first =
        invoiceService.pay(
            card.getId(),
            invoice.getId(),
            null,
            account.getId(),
            new BigDecimal("40.00")
        );

    assertThat(first.invoice().getStatus())
        .isEqualTo(CreditCardInvoiceStatus.PARTIALLY_PAID);
    assertThat(first.invoice().getPaid()).isEqualTo(Money.of("40.00"));
    assertThat(first.invoice().getRemaining()).isEqualTo(Money.of("60.00"));
    assertThat(balance()).isEqualTo(new BigDecimal("960.00"));
    assertThat(committed()).isEqualTo(Money.of("60.00"));
    assertThat(available()).isEqualTo(Money.of("440.00"));

    CreditCardInvoiceApplicationService.PaymentResult second =
        invoiceService.pay(
            card.getId(),
            invoice.getId(),
            null,
            account.getId(),
            new BigDecimal("60.00")
        );

    assertThat(second.invoice().getStatus())
        .isEqualTo(CreditCardInvoiceStatus.PAID);
    assertThat(balance()).isEqualTo(new BigDecimal("900.00"));
    assertThat(available()).isEqualTo(Money.of("500.00"));
    assertThat(transactionRepository.findAll()).isEmpty();
  }

  @Test
  void shouldRejectPaymentAboveRemaining() {
    CreditCardPurchase purchase = createPurchase(
        "100.00",
        LocalDate.of(2026, 5, 10),
        1
    );
    CreditCardInvoice invoice = invoiceFor(purchase, 0);

    assertThatThrownBy(() -> invoiceService.pay(
        card.getId(),
        invoice.getId(),
        null,
        account.getId(),
        new BigDecimal("100.01")
    )).isInstanceOf(IllegalArgumentException.class);

    assertThat(balance()).isEqualTo(new BigDecimal("1000.00"));
    assertThat(invoiceRepository.findById(invoice.getId()).orElseThrow()
        .getPaid()).isEqualTo(Money.zero());
    assertThat(transactionRepository.findAll()).isEmpty();
  }

  @Test
  void shouldEnforceCreditLimitAndLimitUpdates() {
    createPurchase("300.00", LocalDate.of(2026, 5, 10), 1);

    assertThat(available()).isEqualTo(Money.of("200.00"));

    assertThatThrownBy(() -> createPurchase(
        "201.00",
        LocalDate.of(2026, 5, 20),
        1
    )).isInstanceOf(CreditCardLimitExceededException.class);

    assertThatThrownBy(() -> cardService.update(
        card.getId(),
        null,
        null,
        new BigDecimal("250.00"),
        null,
        null
    )).isInstanceOf(IllegalArgumentException.class);

    CreditCard updated = cardService.update(
        card.getId(),
        null,
        null,
        new BigDecimal("400.00"),
        null,
        null
    );

    assertThat(updated.getCreditLimit()).isEqualTo(Money.of("400.00"));
  }

  @Test
  void shouldCancelPurchaseOnlyWhileInvoicesAreOpen() {
    CreditCardPurchase openPurchase = createPurchase(
        "100.00",
        LocalDate.of(2026, 5, 20),
        1
    );
    CreditCardInvoice openInvoice = invoiceFor(openPurchase, 0);

    CreditCardPurchase cancelled = purchaseService.cancel(
        card.getId(),
        openPurchase.getId()
    );

    assertThat(cancelled.getStatus())
        .isEqualTo(CreditCardPurchaseStatus.CANCELLED);
    assertThat(invoiceRepository.findById(openInvoice.getId()).orElseThrow()
        .getTotal()).isEqualTo(Money.zero());
    assertThat(committed()).isEqualTo(Money.zero());

    assertThatThrownBy(() -> purchaseService.cancel(
        card.getId(),
        openPurchase.getId()
    )).isInstanceOf(IllegalStateException.class);

    CreditCardPurchase closedPurchase = createPurchase(
        "100.00",
        LocalDate.of(2026, 5, 10),
        1
    );

    assertThatThrownBy(() -> purchaseService.cancel(
        card.getId(),
        closedPurchase.getId()
    )).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void shouldCancelInstallmentPurchaseOnlyWhenAllInvoicesAreOpen() {
    CreditCardPurchase openPurchase = createPurchase(
        "100.00",
        LocalDate.of(2026, 5, 20),
        2
    );

    CreditCardPurchase cancelled = purchaseService.cancel(
        card.getId(),
        openPurchase.getId()
    );

    assertThat(cancelled.getStatus())
        .isEqualTo(CreditCardPurchaseStatus.CANCELLED);
    assertThat(invoiceFor(openPurchase, 0).getTotal())
        .isEqualTo(Money.zero());
    assertThat(invoiceFor(openPurchase, 1).getTotal())
        .isEqualTo(Money.zero());

    CreditCardPurchase mixedPurchase = createPurchase(
        "100.00",
        LocalDate.of(2026, 5, 9),
        2
    );

    CreditCardInvoice closedInvoice = invoiceFor(mixedPurchase, 0);
    CreditCardInvoice openInvoice = invoiceFor(mixedPurchase, 1);

    assertThat(closedInvoice.getStatus())
        .isEqualTo(CreditCardInvoiceStatus.CLOSED);
    assertThat(openInvoice.getStatus())
        .isEqualTo(CreditCardInvoiceStatus.OPEN);

    assertThatThrownBy(() -> purchaseService.cancel(
        card.getId(),
        mixedPurchase.getId()
    )).isInstanceOf(IllegalStateException.class);

    assertThat(invoiceRepository.findById(closedInvoice.getId())
        .orElseThrow()
        .getTotal()).isEqualTo(Money.of("50.00"));
  }

  @Test
  void shouldBlockClosingDayChangeAfterInvoicesExist() {
    createPurchase("100.00", LocalDate.of(2026, 5, 10), 1);

    assertThatThrownBy(() -> cardService.update(
        card.getId(),
        null,
        null,
        null,
        20,
        null
    )).isInstanceOf(IllegalStateException.class);

    assertThat(cardService.findById(card.getId()).orElseThrow()
        .getClosingDay()).isEqualTo(10);
  }

  @Test
  void shouldAllowDueDayChangeWithoutChangingExistingInvoices() {
    CreditCardPurchase purchase = createPurchase(
        "100.00",
        LocalDate.of(2026, 5, 10),
        1
    );
    CreditCardInvoice invoice = invoiceFor(purchase, 0);
    LocalDate originalDueDate = invoice.getDueDate();

    CreditCard updated = cardService.update(
        card.getId(),
        null,
        null,
        null,
        null,
        25
    );

    assertThat(updated.getDueDay()).isEqualTo(25);
    assertThat(invoiceRepository.findById(invoice.getId()).orElseThrow()
        .getDueDate()).isEqualTo(originalDueDate);
  }

  @Test
  void shouldBlockNewPurchasesAfterDeactivationButPreserveHistory() {
    CreditCardPurchase purchase = createPurchase(
        "100.00",
        LocalDate.of(2026, 5, 10),
        1
    );

    cardService.deactivate(card.getId());

    assertThat(cardService.findAll()).hasSize(1);
    assertThat(cardService.findById(card.getId()).orElseThrow()
        .getStatus().name()).isEqualTo("INACTIVE");
    assertThat(invoiceRepository.findById(
        invoiceFor(purchase, 0).getId()
    )).isPresent();

    assertThatThrownBy(() -> createPurchase(
        "10.00",
        LocalDate.of(2026, 5, 20),
        1
    )).isInstanceOf(CreditCardInactiveException.class);

    // Existing debt can still be settled after deactivation.
    invoiceService.pay(
        card.getId(),
        invoiceFor(purchase, 0).getId(),
        null,
        account.getId(),
        new BigDecimal("100.00")
    );

    assertThat(balance()).isEqualTo(new BigDecimal("900.00"));
  }

  @Test
  void shouldMakePurchaseAndPaymentIdempotent() {
    UUID purchaseId = UUID.randomUUID();
    CreditCardPurchase first = purchaseService.create(
        card.getId(),
        purchaseId,
        new BigDecimal("100.00"),
        "Purchase",
        LocalDate.of(2026, 5, 10),
        null,
        1
    );

    CreditCardPurchase retry = purchaseService.create(
        card.getId(),
        purchaseId,
        new BigDecimal("100.00"),
        "Purchase",
        LocalDate.of(2026, 5, 10),
        null,
        1
    );

    assertThat(retry.getId()).isEqualTo(first.getId());
    assertThat(committed()).isEqualTo(Money.of("100.00"));

    CreditCardInvoice invoice = invoiceFor(first, 0);
    UUID paymentId = UUID.randomUUID();

    CreditCardInvoiceApplicationService.PaymentResult firstPayment =
        invoiceService.pay(
            card.getId(),
            invoice.getId(),
            paymentId,
            account.getId(),
            new BigDecimal("40.00")
        );

    CreditCardInvoiceApplicationService.PaymentResult retryPayment =
        invoiceService.pay(
            card.getId(),
            invoice.getId(),
            paymentId,
            account.getId(),
            new BigDecimal("40.00")
        );

    assertThat(retryPayment.payment().getId())
        .isEqualTo(firstPayment.payment().getId());
    assertThat(balance()).isEqualTo(new BigDecimal("960.00"));
    assertThat(invoiceService.findPayments(
        card.getId(),
        invoice.getId()
    )).hasSize(1);
  }

  private CreditCardPurchase createPurchase(
      String amount,
      LocalDate purchaseDate,
      int installmentCount
  ) {
    return purchaseService.create(
        card.getId(),
        null,
        new BigDecimal(amount),
        "Purchase",
        purchaseDate,
        null,
        installmentCount
    );
  }

  private CreditCardInvoice invoiceFor(
      CreditCardPurchase purchase,
      int installmentIndex
  ) {
    UUID invoiceId = purchase.getInstallments()
        .get(installmentIndex)
        .getInvoiceId();

    return invoiceRepository.findById(invoiceId).orElseThrow();
  }

  private BigDecimal balance() {
    return accountRepository.findById(account.getId())
        .orElseThrow()
        .getBalance()
        .amount();
  }

  private Money committed() {
    CreditCard current = cardService.findById(card.getId()).orElseThrow();

    return cardService.committedAmount(current);
  }

  private Money available() {
    CreditCard current = cardService.findById(card.getId()).orElseThrow();

    return current.availableLimit(cardService.committedAmount(current));
  }
}
