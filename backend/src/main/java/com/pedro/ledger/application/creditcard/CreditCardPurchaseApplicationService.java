package com.pedro.ledger.application.creditcard;

import com.pedro.ledger.domain.category.CategoryNotFoundException;
import com.pedro.ledger.domain.category.CategoryRepository;
import com.pedro.ledger.domain.creditcard.CreditCard;
import com.pedro.ledger.domain.creditcard.CreditCardBillingCycle;
import com.pedro.ledger.domain.creditcard.CreditCardInstallment;
import com.pedro.ledger.domain.creditcard.CreditCardInvoice;
import com.pedro.ledger.domain.creditcard.CreditCardInvoiceNotFoundException;
import com.pedro.ledger.domain.creditcard.CreditCardInvoiceRepository;
import com.pedro.ledger.domain.creditcard.CreditCardNotFoundException;
import com.pedro.ledger.domain.creditcard.CreditCardPurchase;
import com.pedro.ledger.domain.creditcard.CreditCardPurchaseNotFoundException;
import com.pedro.ledger.domain.creditcard.CreditCardPurchaseRepository;
import com.pedro.ledger.domain.creditcard.CreditCardRepository;
import com.pedro.ledger.domain.money.Money;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service responsible for credit card purchases.
 */
@Service
public class CreditCardPurchaseApplicationService {

  private final CreditCardRepository creditCardRepository;
  private final CreditCardInvoiceRepository invoiceRepository;
  private final CreditCardPurchaseRepository purchaseRepository;
  private final CategoryRepository categoryRepository;
  private final Clock clock;

  public CreditCardPurchaseApplicationService(
      CreditCardRepository creditCardRepository,
      CreditCardInvoiceRepository invoiceRepository,
      CreditCardPurchaseRepository purchaseRepository,
      CategoryRepository categoryRepository,
      Clock clock
  ) {
    this.creditCardRepository = creditCardRepository;
    this.invoiceRepository = invoiceRepository;
    this.purchaseRepository = purchaseRepository;
    this.categoryRepository = categoryRepository;
    this.clock = clock;
  }

  /**
   * Registers a credit card purchase and distributes it across invoices.
   *
   * @param cardId card identifier
   * @param purchaseId optional caller-provided purchase identifier
   * @param amount purchase total
   * @param description purchase description
   * @param purchaseDate purchase date
   * @param categoryId optional category identifier
   * @param installmentCount number of installments
   * @return the registered purchase
   */
  @Transactional
  public CreditCardPurchase create(
      UUID cardId,
      UUID purchaseId,
      BigDecimal amount,
      String description,
      LocalDate purchaseDate,
      UUID categoryId,
      int installmentCount
  ) {
    CreditCard card = creditCardRepository.findByIdForUpdate(cardId)
        .orElseThrow(CreditCardNotFoundException::new);

    if (purchaseId != null) {
      Optional<CreditCardPurchase> existing =
          purchaseRepository.findById(purchaseId);

      if (existing.isPresent()) {
        validateSameCard(existing.get(), cardId);
        return existing.get();
      }
    }

    card.ensureActive();
    validateCategory(categoryId);

    Money total = Money.of(amount, card.getCurrency());
    LocalDate today = LocalDate.now(clock);

    card.ensureCanPurchase(total, committedAmount(card));

    LocalDate firstClosingDate = CreditCardBillingCycle.closingDateFor(
        purchaseDate,
        card.getClosingDay()
    );

    List<UUID> invoiceIds = new ArrayList<>(installmentCount);
    Map<UUID, CreditCardInvoice> invoicesById = new HashMap<>();

    for (int index = 0; index < installmentCount; index++) {
      LocalDate closingDate =
          CreditCardBillingCycle.installmentClosingDate(
              firstClosingDate,
              index,
              card.getClosingDay()
          );

      CreditCardInvoice invoice = getOrCreateInvoice(
          card,
          closingDate,
          today
      );

      invoiceIds.add(invoice.getId());
      invoicesById.put(invoice.getId(), invoice);
    }

    CreditCardPurchase purchase = CreditCardPurchase.create(
        purchaseId == null ? UUID.randomUUID() : purchaseId,
        cardId,
        description,
        total,
        purchaseDate,
        categoryId,
        installmentCount,
        invoiceIds,
        Instant.now(clock)
    );

    for (CreditCardInstallment installment : purchase.getInstallments()) {
      invoicesById.get(installment.getInvoiceId())
          .addCharge(installment.getAmount(), today);
    }

    invoicesById.values().forEach(invoiceRepository::save);

    return purchaseRepository.save(purchase);
  }

  public Optional<CreditCardPurchase> findById(UUID id) {
    return purchaseRepository.findById(id);
  }

  public List<CreditCardPurchase> findByCardId(UUID cardId) {
    creditCardRepository.findById(cardId)
        .orElseThrow(CreditCardNotFoundException::new);

    return purchaseRepository.findByCardId(cardId);
  }

  /**
   * Cancels a purchase when all its invoices are still open and unpaid.
   *
   * @param cardId card identifier
   * @param purchaseId purchase identifier
   * @return the cancelled purchase
   */
  @Transactional
  public CreditCardPurchase cancel(UUID cardId, UUID purchaseId) {
    creditCardRepository.findByIdForUpdate(cardId)
        .orElseThrow(CreditCardNotFoundException::new);

    CreditCardPurchase purchase = purchaseRepository.findById(purchaseId)
        .orElseThrow(CreditCardPurchaseNotFoundException::new);

    validateSameCard(purchase, cardId);

    if (purchase.isCancelled()) {
      throw new IllegalStateException("Purchase is already cancelled");
    }

    LocalDate today = LocalDate.now(clock);
    Map<UUID, CreditCardInvoice> invoicesById = new HashMap<>();

    for (CreditCardInstallment installment : purchase.getInstallments()) {
      CreditCardInvoice invoice = invoiceRepository.findById(
          installment.getInvoiceId()
      ).orElseThrow(CreditCardInvoiceNotFoundException::new);

      invoice.refreshStatus(today);

      if (!invoice.isOpen() || invoice.hasPayments()) {
        throw new IllegalStateException(
            "Purchase can only be cancelled while all invoices are open"
        );
      }

      invoicesById.put(invoice.getId(), invoice);
    }

    purchase.cancel();

    for (CreditCardInstallment installment : purchase.getInstallments()) {
      invoicesById.get(installment.getInvoiceId())
          .removeCharge(installment.getAmount());
    }

    invoicesById.values().forEach(invoiceRepository::save);

    return purchaseRepository.save(purchase);
  }

  private CreditCardInvoice getOrCreateInvoice(
      CreditCard card,
      LocalDate closingDate,
      LocalDate today
  ) {
    return invoiceRepository
        .findByCardIdAndClosingDate(card.getId(), closingDate)
        .orElseGet(() -> createInvoice(card, closingDate, today));
  }

  private CreditCardInvoice createInvoice(
      CreditCard card,
      LocalDate closingDate,
      LocalDate today
  ) {
    CreditCardInvoice invoice = CreditCardInvoice.open(
        card.getId(),
        CreditCardBillingCycle.cycleStart(
            closingDate,
            card.getClosingDay()
        ),
        closingDate,
        closingDate,
        CreditCardBillingCycle.dueDate(
            closingDate,
            card.getDueDay()
        ),
        card.getCurrency(),
        today
    );

    return invoiceRepository.save(invoice);
  }

  private Money committedAmount(CreditCard card) {
    Money committed = Money.zero(card.getCurrency());

    for (CreditCardInvoice invoice : invoiceRepository.findByCardId(
        card.getId()
    )) {
      committed = committed.add(invoice.getRemaining());
    }

    return committed;
  }

  private void validateCategory(UUID categoryId) {
    if (categoryId == null) {
      return;
    }

    categoryRepository.findById(categoryId)
        .orElseThrow(CategoryNotFoundException::new);
  }

  private void validateSameCard(
      CreditCardPurchase purchase,
      UUID cardId
  ) {
    if (!purchase.getCardId().equals(cardId)) {
      throw new IllegalArgumentException(
          "Purchase does not belong to the informed credit card"
      );
    }
  }
}
