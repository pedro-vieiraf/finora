package com.pedro.ledger.application.creditcard;

import com.pedro.ledger.domain.account.Account;
import com.pedro.ledger.domain.account.AccountNotFoundException;
import com.pedro.ledger.domain.account.AccountRepository;
import com.pedro.ledger.domain.creditcard.CreditCardInvoice;
import com.pedro.ledger.domain.creditcard.CreditCardInvoiceNotFoundException;
import com.pedro.ledger.domain.creditcard.CreditCardInvoicePayment;
import com.pedro.ledger.domain.creditcard.CreditCardInvoicePaymentRepository;
import com.pedro.ledger.domain.creditcard.CreditCardInvoiceRepository;
import com.pedro.ledger.domain.creditcard.CreditCardNotFoundException;
import com.pedro.ledger.domain.creditcard.CreditCardPurchase;
import com.pedro.ledger.domain.creditcard.CreditCardPurchaseRepository;
import com.pedro.ledger.domain.creditcard.CreditCardRepository;
import com.pedro.ledger.domain.money.CurrencyMismatchException;
import com.pedro.ledger.domain.money.Money;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service responsible for credit card invoices and payments.
 */
@Service
public class CreditCardInvoiceApplicationService {

  private final CreditCardRepository creditCardRepository;
  private final CreditCardInvoiceRepository invoiceRepository;
  private final CreditCardPurchaseRepository purchaseRepository;
  private final CreditCardInvoicePaymentRepository paymentRepository;
  private final AccountRepository accountRepository;
  private final Clock clock;

  public CreditCardInvoiceApplicationService(
      CreditCardRepository creditCardRepository,
      CreditCardInvoiceRepository invoiceRepository,
      CreditCardPurchaseRepository purchaseRepository,
      CreditCardInvoicePaymentRepository paymentRepository,
      AccountRepository accountRepository,
      Clock clock
  ) {
    this.creditCardRepository = creditCardRepository;
    this.invoiceRepository = invoiceRepository;
    this.purchaseRepository = purchaseRepository;
    this.paymentRepository = paymentRepository;
    this.accountRepository = accountRepository;
    this.clock = clock;
  }

  public List<CreditCardInvoice> findByCardId(UUID cardId) {
    creditCardRepository.findById(cardId)
        .orElseThrow(CreditCardNotFoundException::new);

    LocalDate today = LocalDate.now(clock);
    List<CreditCardInvoice> invoices =
        invoiceRepository.findByCardId(cardId);

    invoices.forEach(invoice -> invoice.refreshStatus(today));

    return invoices;
  }

  public CreditCardInvoice findById(UUID cardId, UUID invoiceId) {
    creditCardRepository.findById(cardId)
        .orElseThrow(CreditCardNotFoundException::new);

    CreditCardInvoice invoice = invoiceRepository.findById(invoiceId)
        .orElseThrow(CreditCardInvoiceNotFoundException::new);

    validateBelongsToCard(invoice, cardId);
    invoice.refreshStatus(LocalDate.now(clock));

    return invoice;
  }

  public List<CreditCardPurchase> findPurchases(
      UUID cardId,
      UUID invoiceId
  ) {
    findById(cardId, invoiceId);

    return purchaseRepository.findByInvoiceId(invoiceId);
  }

  public List<CreditCardInvoicePayment> findPayments(
      UUID cardId,
      UUID invoiceId
  ) {
    findById(cardId, invoiceId);

    return paymentRepository.findByInvoiceId(invoiceId);
  }

  /**
   * Pays an invoice partially or fully.
   *
   * <p>The paid amount is debited from the informed bank account. No bank
   * transaction is created, because the card purchases already represent the
   * expenses.
   *
   * @param cardId card identifier
   * @param invoiceId invoice identifier
   * @param paymentId optional caller-provided payment identifier
   * @param accountId source bank account identifier
   * @param amount payment amount
   * @return the payment and the updated invoice
   */
  @Transactional
  public PaymentResult pay(
      UUID cardId,
      UUID invoiceId,
      UUID paymentId,
      UUID accountId,
      BigDecimal amount
  ) {
    creditCardRepository.findByIdForUpdate(cardId)
        .orElseThrow(CreditCardNotFoundException::new);

    if (paymentId != null) {
      Optional<CreditCardInvoicePayment> existing =
          paymentRepository.findById(paymentId);

      if (existing.isPresent()) {
        CreditCardInvoicePayment payment = existing.get();

        if (!payment.getInvoiceId().equals(invoiceId)
            || !payment.getAccountId().equals(accountId)) {
          throw new IllegalArgumentException(
              "Payment identifier already belongs to another operation"
          );
        }

        CreditCardInvoice invoice = invoiceRepository.findById(invoiceId)
            .orElseThrow(CreditCardInvoiceNotFoundException::new);

        validateBelongsToCard(invoice, cardId);
        invoice.refreshStatus(LocalDate.now(clock));

        return new PaymentResult(payment, invoice);
      }
    }

    CreditCardInvoice invoice = invoiceRepository.findByIdForUpdate(
        invoiceId
    ).orElseThrow(CreditCardInvoiceNotFoundException::new);

    validateBelongsToCard(invoice, cardId);
    invoice.refreshStatus(LocalDate.now(clock));

    Account account = accountRepository.findByIdForUpdate(accountId)
        .orElseThrow(() -> new AccountNotFoundException("Account not found"));

    account.requireManualPostingAllowed();
    Money paymentAmount = Money.of(amount, invoice.getCurrency());

    if (!account.getBalance().currency().equals(invoice.getCurrency())) {
      throw new CurrencyMismatchException(
          account.getBalance().currency(),
          invoice.getCurrency()
      );
    }

    invoice.registerPayment(paymentAmount);
    account.debit(paymentAmount);

    CreditCardInvoicePayment payment = CreditCardInvoicePayment.create(
        paymentId == null ? UUID.randomUUID() : paymentId,
        invoiceId,
        accountId,
        paymentAmount,
        Instant.now(clock)
    );

    accountRepository.save(account);
    CreditCardInvoice savedInvoice = invoiceRepository.save(invoice);

    return new PaymentResult(
        paymentRepository.save(payment),
        savedInvoice
    );
  }

  /**
   * Result of an invoice payment operation.
   *
   * @param payment persisted payment
   * @param invoice updated invoice
   */
  public record PaymentResult(
      CreditCardInvoicePayment payment,
      CreditCardInvoice invoice
  ) {
  }

  private void validateBelongsToCard(
      CreditCardInvoice invoice,
      UUID cardId
  ) {
    if (!invoice.belongsTo(cardId)) {
      throw new CreditCardInvoiceNotFoundException();
    }
  }
}
