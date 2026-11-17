package com.pedro.ledger.application.creditcard;

import com.pedro.ledger.domain.creditcard.CreditCard;
import com.pedro.ledger.domain.creditcard.CreditCardInvoice;
import com.pedro.ledger.domain.creditcard.CreditCardInvoiceRepository;
import com.pedro.ledger.domain.creditcard.CreditCardNotFoundException;
import com.pedro.ledger.domain.creditcard.CreditCardRepository;
import com.pedro.ledger.domain.money.Money;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service responsible for credit card use cases.
 */
@Service
public class CreditCardApplicationService {

  private final CreditCardRepository creditCardRepository;
  private final CreditCardInvoiceRepository invoiceRepository;

  public CreditCardApplicationService(
      CreditCardRepository creditCardRepository,
      CreditCardInvoiceRepository invoiceRepository
  ) {
    this.creditCardRepository = creditCardRepository;
    this.invoiceRepository = invoiceRepository;
  }

  @Transactional
  public CreditCard create(
      String name,
      String issuer,
      BigDecimal creditLimit,
      int closingDay,
      int dueDay,
      String currencyCode
  ) {
    Currency currency = currencyCode == null
        ? Currency.getInstance("BRL")
        : Currency.getInstance(currencyCode);

    CreditCard card = CreditCard.open(
        name,
        issuer,
        Money.of(creditLimit, currency),
        closingDay,
        dueDay
    );

    return creditCardRepository.save(card);
  }

  public Optional<CreditCard> findById(UUID id) {
    return creditCardRepository.findById(id);
  }

  public List<CreditCard> findAll() {
    return creditCardRepository.findAll();
  }

  @Transactional
  public CreditCard update(
      UUID id,
      String name,
      String issuer,
      BigDecimal creditLimit,
      Integer closingDay,
      Integer dueDay
  ) {
    CreditCard card = getByIdForUpdateOrThrow(id);
    Money committed = committedAmount(card);

    if (name != null) {
      card.rename(name);
    }

    if (issuer != null) {
      card.changeIssuer(issuer);
    }

    if (creditLimit != null) {
      card.changeLimit(
          Money.of(creditLimit, card.getCurrency()),
          committed
      );
    }

    if (closingDay != null) {
      card.changeClosingDay(closingDay);
    }

    if (dueDay != null) {
      card.changeDueDay(dueDay);
    }

    return creditCardRepository.save(card);
  }

  @Transactional
  public void deactivate(UUID id) {
    CreditCard card = getByIdForUpdateOrThrow(id);

    card.deactivate();

    creditCardRepository.save(card);
  }

  @Transactional
  public CreditCard activate(UUID id) {
    CreditCard card = getByIdForUpdateOrThrow(id);

    card.activate();

    return creditCardRepository.save(card);
  }

  /**
   * Calculates the amount committed across all invoice balances.
   *
   * @param card credit card
   * @return outstanding amount
   */
  public Money committedAmount(CreditCard card) {
    Money committed = Money.zero(card.getCurrency());

    for (CreditCardInvoice invoice : invoiceRepository.findByCardId(
        card.getId()
    )) {
      committed = committed.add(invoice.getRemaining());
    }

    return committed;
  }

  private CreditCard getByIdForUpdateOrThrow(UUID id) {
    return creditCardRepository.findByIdForUpdate(id)
        .orElseThrow(CreditCardNotFoundException::new);
  }
}
