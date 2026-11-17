package com.pedro.ledger.domain.creditcard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pedro.ledger.domain.money.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CreditCardPurchaseTest {

  private static final UUID CARD_ID = UUID.randomUUID();
  private static final Instant CREATED_AT = Instant.parse(
      "2026-05-15T12:00:00Z"
  );

  @Test
  void shouldCreateSingleInstallmentPurchase() {
    CreditCardPurchase purchase = create(
        "100.00",
        1,
        List.of(UUID.randomUUID())
    );

    assertThat(purchase.getStatus())
        .isEqualTo(CreditCardPurchaseStatus.ACTIVE);
    assertThat(purchase.getInstallments()).hasSize(1);
    assertThat(purchase.getInstallments().get(0).getAmount())
        .isEqualTo(Money.of("100.00"));
    assertThat(purchase.getInstallments().get(0).getNumber())
        .isEqualTo(1);
  }

  @Test
  void shouldDistributeInstallmentsWithoutCentDifference() {
    CreditCardPurchase purchase = create(
        "100.00",
        3,
        List.of(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID()
        )
    );

    assertThat(purchase.getInstallments())
        .extracting(CreditCardInstallment::getAmount)
        .containsExactly(
            Money.of("33.34"),
            Money.of("33.33"),
            Money.of("33.33")
        );

    assertThat(sum(purchase)).isEqualTo(Money.of("100.00"));
  }

  @Test
  void shouldDistributeSmallAmountAcrossAllowedInstallments() {
    CreditCardPurchase purchase = create(
        "0.03",
        3,
        List.of(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID()
        )
    );

    assertThat(sum(purchase)).isEqualTo(Money.of("0.03"));
  }

  @Test
  void shouldRejectMoreInstallmentsThanCents() {
    assertThatThrownBy(() -> create(
        "0.02",
        3,
        List.of(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID()
        )
    )).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldRejectInvoiceListWithWrongSize() {
    assertThatThrownBy(() -> create(
        "100.00",
        2,
        List.of(UUID.randomUUID())
    )).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldRejectInvalidInstallmentCount() {
    assertThatThrownBy(() -> create(
        "100.00",
        0,
        List.of()
    )).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldCancelPurchaseOnlyOnce() {
    CreditCardPurchase purchase = create(
        "100.00",
        1,
        List.of(UUID.randomUUID())
    );

    purchase.cancel();

    assertThat(purchase.getStatus())
        .isEqualTo(CreditCardPurchaseStatus.CANCELLED);

    assertThatThrownBy(purchase::cancel)
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void shouldRejectRestoreWithInstallmentsThatDoNotSumTotal() {
    UUID invoiceId = UUID.randomUUID();
    CreditCardInstallment installment = new CreditCardInstallment(
        UUID.randomUUID(),
        1,
        Money.of("90.00"),
        invoiceId
    );

    assertThatThrownBy(() -> CreditCardPurchase.restore(
        UUID.randomUUID(),
        CARD_ID,
        "Purchase",
        Money.of("100.00"),
        LocalDate.of(2026, 5, 10),
        null,
        1,
        List.of(installment),
        CREATED_AT,
        CreditCardPurchaseStatus.ACTIVE
    )).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldRejectBlankDescriptionAndInvalidAmount() {
    assertThatThrownBy(() -> CreditCardPurchase.create(
        UUID.randomUUID(),
        CARD_ID,
        " ",
        Money.of("100.00"),
        LocalDate.of(2026, 5, 10),
        null,
        1,
        List.of(UUID.randomUUID()),
        CREATED_AT
    )).isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(() -> create(
        "0.00",
        1,
        List.of(UUID.randomUUID())
    )).isInstanceOf(IllegalArgumentException.class);
  }

  private static CreditCardPurchase create(
      String amount,
      int installmentCount,
      List<UUID> invoiceIds
  ) {
    return CreditCardPurchase.create(
        UUID.randomUUID(),
        CARD_ID,
        "Purchase",
        Money.of(amount),
        LocalDate.of(2026, 5, 10),
        null,
        installmentCount,
        invoiceIds,
        CREATED_AT
    );
  }

  private static Money sum(CreditCardPurchase purchase) {
    return purchase.getInstallments()
        .stream()
        .map(CreditCardInstallment::getAmount)
        .reduce(Money.zero(), Money::add);
  }
}
