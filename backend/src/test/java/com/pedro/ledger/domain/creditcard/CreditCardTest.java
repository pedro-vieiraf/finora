package com.pedro.ledger.domain.creditcard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pedro.ledger.domain.money.CurrencyMismatchException;
import com.pedro.ledger.domain.money.Money;
import java.util.Currency;
import org.junit.jupiter.api.Test;

class CreditCardTest {

  private static final Currency BRL = Currency.getInstance("BRL");

  @Test
  void shouldOpenActiveCreditCard() {
    CreditCard card = CreditCard.open(
        "  Platinum  ",
        "  Bank  ",
        Money.of("5000.00"),
        10,
        20
    );

    assertThat(card.getId()).isNotNull();
    assertThat(card.getName()).isEqualTo("Platinum");
    assertThat(card.getIssuer()).isEqualTo("Bank");
    assertThat(card.getCreditLimit()).isEqualTo(Money.of("5000.00"));
    assertThat(card.getClosingDay()).isEqualTo(10);
    assertThat(card.getDueDay()).isEqualTo(20);
    assertThat(card.getStatus()).isEqualTo(CreditCardStatus.ACTIVE);
    assertThat(card.getCurrency()).isEqualTo(BRL);
  }

  @Test
  void shouldCalculateAvailableLimitFromCommittedAmount() {
    CreditCard card = openCard("5000.00");

    assertThat(card.availableLimit(Money.of("1200.00")))
        .isEqualTo(Money.of("3800.00"));
  }

  @Test
  void shouldAcceptPurchaseEqualToAvailableLimit() {
    CreditCard card = openCard("1000.00");

    card.ensureCanPurchase(Money.of("1000.00"), Money.zero());
  }

  @Test
  void shouldRejectPurchaseAboveAvailableLimit() {
    CreditCard card = openCard("1000.00");

    assertThatThrownBy(() -> card.ensureCanPurchase(
        Money.of("1000.01"),
        Money.zero()
    )).isInstanceOf(CreditCardLimitExceededException.class);
  }

  @Test
  void shouldRejectCommittedAmountWithDifferentCurrency() {
    CreditCard card = openCard("1000.00");

    assertThatThrownBy(() -> card.availableLimit(
        Money.of("10.00", Currency.getInstance("USD"))
    )).isInstanceOf(CurrencyMismatchException.class);
  }

  @Test
  void shouldChangeLimitOnlyAboveCommittedAmount() {
    CreditCard card = openCard("1000.00");

    card.changeLimit(Money.of("800.00"), Money.of("500.00"));
    assertThat(card.getCreditLimit()).isEqualTo(Money.of("800.00"));

    assertThatThrownBy(() -> card.changeLimit(
        Money.of("400.00"),
        Money.of("500.00")
    )).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldDeactivateAndReactivateCard() {
    CreditCard card = openCard("1000.00");

    card.deactivate();
    assertThat(card.getStatus()).isEqualTo(CreditCardStatus.INACTIVE);

    card.activate();
    assertThat(card.getStatus()).isEqualTo(CreditCardStatus.ACTIVE);
  }

  @Test
  void shouldDeleteInactiveCard() {
    CreditCard card = openCard("1000.00");

    card.deactivate();
    card.delete();

    assertThat(card.getStatus()).isEqualTo(CreditCardStatus.DELETED);
  }

  @Test
  void shouldNotDeleteActiveCard() {
    CreditCard card = openCard("1000.00");

    assertThatThrownBy(card::delete)
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Only inactive credit cards can be deleted");

    assertThat(card.getStatus()).isEqualTo(CreditCardStatus.ACTIVE);
  }

  @Test
  void shouldNotReactivateDeletedCard() {
    CreditCard card = openCard("1000.00");

    card.deactivate();
    card.delete();

    assertThatThrownBy(card::activate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Deleted credit cards cannot be reactivated");

    assertThat(card.getStatus()).isEqualTo(CreditCardStatus.DELETED);
  }

  @Test
  void shouldNotDeactivateDeletedCard() {
    CreditCard card = openCard("1000.00");

    card.deactivate();
    card.delete();

    assertThatThrownBy(card::deactivate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Deleted credit cards cannot be deactivated");

    assertThat(card.getStatus()).isEqualTo(CreditCardStatus.DELETED);
  }

  @Test
  void shouldRejectInvalidCardData() {
    assertThatThrownBy(() -> CreditCard.open(
        " ",
        "Bank",
        Money.of("1000.00"),
        10,
        20
    )).isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(() -> CreditCard.open(
        "Card",
        " ",
        Money.of("1000.00"),
        10,
        20
    )).isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(() -> CreditCard.open(
        "Card",
        "Bank",
        Money.zero(),
        10,
        20
    )).isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(() -> CreditCard.open(
        "Card",
        "Bank",
        Money.of("1000.00"),
        0,
        20
    )).isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(() -> CreditCard.open(
        "Card",
        "Bank",
        Money.of("1000.00"),
        10,
        32
    )).isInstanceOf(IllegalArgumentException.class);
  }

  private static CreditCard openCard(String limit) {
    return CreditCard.open(
        "Card",
        "Bank",
        Money.of(limit),
        10,
        20
    );
  }
}
