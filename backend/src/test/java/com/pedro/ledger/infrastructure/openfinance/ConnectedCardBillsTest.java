package com.pedro.ledger.infrastructure.openfinance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConnectedCardBillsTest {

  @Test
  void usesBankTotalsAndExplicitInvoiceLinksAndCachesTheResult() {
    var store = mock(OpenFinanceImportStore.class);
    var client = mock(PluggyClient.class);
    var item = UUID.randomUUID();
    var account = UUID.randomUUID();
    var card = UUID.randomUUID();
    var cutoff = LocalDate.parse("2026-10-01");
    var today = LocalDate.parse("2026-11-22");
    var now = Instant.parse("2026-11-22T12:00:00Z");
    var selection = new OpenFinanceInitialImport.Selection("Nubank", item, account);
    when(store.linkedBanks()).thenReturn(List.of(new OpenFinanceImportStore.LinkedBank(
        UUID.randomUUID(), selection, cutoff)));
    when(client.listCreditCards(item)).thenReturn(List.of(
        new PluggyClient.CreditAccount(card, "CREDIT_CARD", "BRL",
            new BigDecimal("35.00"), new PluggyClient.CreditData("2026-12-12"))));
    var payment = new PluggyClient.BillPayment("payment", "2026-11-12T00:00:00Z",
        new BigDecimal("50.00"), "BRL");
    when(client.listCardBills(card)).thenReturn(List.of(
        new PluggyClient.CardBill("previous", "2026-09-12", null, BigDecimal.TEN, "BRL", List.of()),
        new PluggyClient.CardBill("current", "2026-11-12T00:00:00", "2026-11-05",
            new BigDecimal("50.00"), "BRL", List.of(payment))));
    var assigned = new PluggyClient.Transaction("purchase", card, "Compra", now, "BRL", BigDecimal.TEN,
        null, "DEBIT", "POSTED", null, new PluggyClient.CardTransactionMetadata("current", 1, 2, null, null));
    var pending = new PluggyClient.Transaction("pending", card, "Pendente", now, "BRL", BigDecimal.TEN,
        null, "DEBIT", "PENDING");
    var page = new PluggyClient.TransactionPage(List.of(assigned, pending), "?after=next");
    when(client.listTransactions(card, cutoff, today.plusMonths(6))).thenReturn(page);
    when(client.nextPage(page)).thenReturn(new PluggyClient.TransactionPage(List.of(assigned), null));

    var service = new ConnectedCardBills(store, Clock.fixed(now, ZoneOffset.UTC), client);

    assertThat(service.findAll()).singleElement().satisfies(result -> {
      assertThat(result.bankName()).isEqualTo("Nubank");
      assertThat(result.nextDueDate()).isEqualTo("2026-12-12");
      assertThat(result.openBalance()).isEqualByComparingTo("35.00");
      assertThat(result.bills()).singleElement().satisfies(bill -> {
        assertThat(bill.dueDate()).isEqualTo("2026-11-12");
        assertThat(bill.total()).isEqualByComparingTo("50.00");
        assertThat(bill.payments()).singleElement()
            .satisfies(entry -> assertThat(entry.amount()).isEqualByComparingTo("50.00"));
        assertThat(bill.entries()).singleElement().satisfies(entry -> {
          assertThat(entry.id()).isEqualTo("purchase");
          assertThat(entry.totalInstallments()).isEqualTo(2);
        });
      });
    });
    service.findAll();
    verify(client).listCardBills(card);

    when(client.listCreditCards(item)).thenReturn(List.of(
        new PluggyClient.CreditAccount(card, "CREDIT_CARD", "BRL",
            new BigDecimal("35.00"), new PluggyClient.CreditData("2026-11-12"))));
    var sameDueDate = new ConnectedCardBills(store, Clock.fixed(now, ZoneOffset.UTC), client);
    assertThat(sameDueDate.findAll()).singleElement().satisfies(result -> {
      assertThat(result.nextDueDate()).isNull();
      assertThat(result.openBalance()).isEqualByComparingTo("35.00");
    });
  }
}
