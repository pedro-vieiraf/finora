package com.pedro.ledger.domain.creditcard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pedro.ledger.domain.money.Money;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CreditCardInvoiceTest {

  private static final UUID CARD_ID = UUID.randomUUID();
  private static final LocalDate TODAY = LocalDate.of(2026, 5, 15);

  @Test
  void shouldOpenInvoiceAsOpenBeforeClosingDate() {
    CreditCardInvoice invoice = invoice(LocalDate.of(2026, 6, 10));

    assertThat(invoice.getStatus())
        .isEqualTo(CreditCardInvoiceStatus.OPEN);
    assertThat(invoice.getTotal()).isEqualTo(Money.zero());
    assertThat(invoice.getPaid()).isEqualTo(Money.zero());
    assertThat(invoice.getRemaining()).isEqualTo(Money.zero());
  }

  @Test
  void shouldOpenInvoiceAsClosedAfterClosingDate() {
    CreditCardInvoice invoice = invoice(LocalDate.of(2026, 5, 10));

    assertThat(invoice.getStatus())
        .isEqualTo(CreditCardInvoiceStatus.CLOSED);
  }

  @Test
  void shouldKeepInvoiceOpenOnClosingDay() {
    CreditCardInvoice invoice = invoice(TODAY);

    assertThat(invoice.getStatus())
        .isEqualTo(CreditCardInvoiceStatus.OPEN);
  }

  @Test
  void shouldAddAndRemoveChargeWhileOpen() {
    CreditCardInvoice invoice = invoice(LocalDate.of(2026, 6, 10));

    invoice.addCharge(Money.of("100.00"), TODAY);

    assertThat(invoice.getTotal()).isEqualTo(Money.of("100.00"));

    invoice.removeCharge(Money.of("40.00"));

    assertThat(invoice.getTotal()).isEqualTo(Money.of("60.00"));
    assertThat(invoice.getStatus())
        .isEqualTo(CreditCardInvoiceStatus.OPEN);
  }

  @Test
  void shouldRejectPaymentBeforeClosing() {
    CreditCardInvoice invoice = invoice(LocalDate.of(2026, 6, 10));
    invoice.addCharge(Money.of("100.00"), TODAY);

    assertThatThrownBy(() -> invoice.registerPayment(
        Money.of("10.00")
    )).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void shouldRegisterPartialAndFullPayments() {
    CreditCardInvoice invoice = invoice(LocalDate.of(2026, 5, 10));
    invoice.addCharge(Money.of("100.00"), TODAY);

    invoice.registerPayment(Money.of("40.00"));

    assertThat(invoice.getPaid()).isEqualTo(Money.of("40.00"));
    assertThat(invoice.getRemaining()).isEqualTo(Money.of("60.00"));
    assertThat(invoice.getStatus())
        .isEqualTo(CreditCardInvoiceStatus.PARTIALLY_PAID);

    invoice.registerPayment(Money.of("60.00"));

    assertThat(invoice.getPaid()).isEqualTo(Money.of("100.00"));
    assertThat(invoice.getRemaining()).isEqualTo(Money.zero());
    assertThat(invoice.getStatus())
        .isEqualTo(CreditCardInvoiceStatus.PAID);
  }

  @Test
  void shouldRejectPaymentAboveRemaining() {
    CreditCardInvoice invoice = invoice(LocalDate.of(2026, 5, 10));
    invoice.addCharge(Money.of("100.00"), TODAY);

    assertThatThrownBy(() -> invoice.registerPayment(
        Money.of("100.01")
    )).isInstanceOf(IllegalArgumentException.class);

    assertThat(invoice.getPaid()).isEqualTo(Money.zero());
  }

  @Test
  void shouldRejectAdditionalPaymentWhenPaid() {
    CreditCardInvoice invoice = invoice(LocalDate.of(2026, 5, 10));
    invoice.addCharge(Money.of("100.00"), TODAY);
    invoice.registerPayment(Money.of("100.00"));

    assertThatThrownBy(() -> invoice.registerPayment(
        Money.of("1.00")
    )).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void shouldRejectChargeRemovalAfterClosingOrPayment() {
    CreditCardInvoice closed = invoice(LocalDate.of(2026, 5, 10));
    closed.addCharge(Money.of("100.00"), TODAY);

    assertThatThrownBy(() -> closed.removeCharge(Money.of("10.00")))
        .isInstanceOf(IllegalStateException.class);

    CreditCardInvoice partiallyPaid = invoice(
        LocalDate.of(2026, 5, 10)
    );
    partiallyPaid.addCharge(Money.of("100.00"), TODAY);
    partiallyPaid.registerPayment(Money.of("10.00"));

    assertThatThrownBy(() -> partiallyPaid.removeCharge(
        Money.of("10.00")
    )).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void shouldRejectPaidInvoiceCharge() {
    CreditCardInvoice invoice = invoice(LocalDate.of(2026, 5, 10));
    invoice.addCharge(Money.of("100.00"), TODAY);
    invoice.registerPayment(Money.of("100.00"));

    assertThatThrownBy(() -> invoice.addCharge(
        Money.of("10.00"),
        TODAY
    )).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void shouldRejectInvalidInvoiceData() {
    assertThatThrownBy(() -> CreditCardInvoice.open(
        CARD_ID,
        LocalDate.of(2026, 6, 1),
        LocalDate.of(2026, 6, 10),
        LocalDate.of(2026, 6, 10),
        LocalDate.of(2026, 6, 10),
        Money.of("0.00").currency(),
        TODAY
    )).isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(() -> CreditCardInvoice.restore(
        UUID.randomUUID(),
        CARD_ID,
        LocalDate.of(2026, 6, 1),
        LocalDate.of(2026, 6, 10),
        LocalDate.of(2026, 6, 10),
        LocalDate.of(2026, 7, 10),
        Money.of("0.00").currency(),
        Money.of("100.00"),
        Money.of("101.00"),
        CreditCardInvoiceStatus.PAID
    )).isInstanceOf(IllegalArgumentException.class);
  }

  private static CreditCardInvoice invoice(LocalDate closingDate) {
    return CreditCardInvoice.open(
        CARD_ID,
        closingDate.minusMonths(1).plusDays(1),
        closingDate,
        closingDate,
        closingDate.plusMonths(1).withDayOfMonth(10),
        Money.of("0.00").currency(),
        TODAY
    );
  }
}
