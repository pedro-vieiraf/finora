package com.pedro.ledger.infrastructure.openfinance;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConnectedCardForecastTest {

  private static final UUID CARD = UUID.randomUUID();

  @Test
  void groupsOnlyUnbilledTransactionsWithAForecastMonthAndIgnoresPayments() {
    var forecasts = ConnectedCardForecast.from(List.of(
        entry("a", "2026-12", "DEBIT", "50.10", null, null),
        entry("b", "2027-01", "DEBIT", "30.25", null, null),
        entry("refund", "2026-12", "CREDIT", "4.20", null, "ESTORNO"),
        entry("payment", "2026-12", "CREDIT", "10.00", null, "PAGAMENTO_FATURA"),
        entry("closed", "2026-12", "DEBIT", "85.00", "bill", null),
        entry("undated", null, "DEBIT", "12.00", null, null),
        entry("history", "2026-11", "DEBIT", "12.00", null, null)
    ), "BRL", YearMonth.parse("2026-12"), YearMonth.parse("2027-05"));

    assertThat(forecasts).containsExactly(
        new ConnectedCardBills.Forecast("2026-12", new BigDecimal("45.90"), "BRL", false),
        new ConnectedCardBills.Forecast("2027-01", new BigDecimal("30.25"), "BRL", false));
  }

  @Test
  void projectsRemainingInstallmentsOnlyForMonthsWithoutProviderForecasts() {
    var latest = new ConnectedCardBills.Bill("november", LocalDate.parse("2026-11-26"), null,
        BigDecimal.TEN, "BRL", List.of(), List.of(
            new ConnectedCardBills.Entry("part", null, "Compra", "DEBIT",
                new BigDecimal("14.25"), "BRL", 2, 4),
            new ConnectedCardBills.Entry("once", null, "Compra avulsa", "DEBIT",
                BigDecimal.TEN, "BRL", 1, 1),
            new ConnectedCardBills.Entry("refund", null, "Estorno", "CREDIT",
                BigDecimal.TEN, "BRL", 1, 3)
        ));
    var december = new ConnectedCardBills.Forecast("2026-12", new BigDecimal("20.00"), "BRL", false);

    assertThat(ConnectedCardForecast.withInstallmentEstimates(List.of(december), latest,
        YearMonth.parse("2026-12"), YearMonth.parse("2027-05"), LocalDate.parse("2026-11-22"), List.of())).containsExactly(
        december, new ConnectedCardBills.Forecast("2027-01", new BigDecimal("14.25"), "BRL", true));
    assertThat(ConnectedCardForecast.withInstallmentEstimates(List.of(), latest,
        YearMonth.parse("2026-12"), YearMonth.parse("2027-05"), LocalDate.parse("2026-11-22"), List.of()))
        .extracting(ConnectedCardBills.Forecast::month).containsExactly("2026-12", "2027-01");
  }

  @Test
  void includesOnlyKnownSingleChargesAfterTheLastClosingInTheNextCycleEstimate() {
    var latest = new ConnectedCardBills.Bill("november", LocalDate.parse("2026-11-26"),
        LocalDate.parse("2026-11-19"), BigDecimal.TEN, "BRL", List.of(), List.of());
    var charge = new PluggyClient.Transaction("subscription", CARD, "Assinatura",
        Instant.parse("2026-11-22T12:00:00Z"), "BRL", new BigDecimal("20.00"), null,
        "DEBIT", "PENDING", null, new PluggyClient.CardTransactionMetadata(null, 1, 1, null, "SINGLE"));
    var unknown = entry("unknown", null, "DEBIT", "30.25", null, null);

    assertThat(ConnectedCardForecast.withInstallmentEstimates(List.of(), latest,
        YearMonth.parse("2026-12"), YearMonth.parse("2027-05"),
        LocalDate.parse("2026-11-22"), List.of(charge, unknown))).containsExactly(
            new ConnectedCardBills.Forecast("2026-12", new BigDecimal("20.00"), "BRL", true));
  }

  private static PluggyClient.Transaction entry(
      String id, String month, String type, String amount, String billId, String operation
  ) {
    return new PluggyClient.Transaction(id, CARD, "Compra", Instant.parse("2026-11-22T12:00:00Z"), "BRL",
        new BigDecimal(amount), null, type, "PENDING", operation,
        new PluggyClient.CardTransactionMetadata(billId, 1, 2, month, null));
  }
}
