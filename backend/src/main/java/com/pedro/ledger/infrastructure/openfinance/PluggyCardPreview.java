package com.pedro.ledger.infrastructure.openfinance;

import java.io.PrintStream;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.stream.Collectors;
import java.util.UUID;

final class PluggyCardPreview {

  private PluggyCardPreview() {}

  static void print(PluggyClient client, UUID itemId, LocalDate from, LocalDate to, PrintStream out) {
    var cards = client.listCreditCards(itemId);
    out.printf("Cartões encontrados: %d%n", cards.size());
    for (var card : cards) {
      var bills = client.listCardBills(card.id());
      long billsWithPayments = bills.stream()
          .filter(bill -> bill.payments() != null && !bill.payments().isEmpty())
          .count();

      var billsInPeriod = bills.stream()
          .filter(bill -> dueDateInPeriod(bill.dueDate(), from, to))
          .toList();
      var billIds = billsInPeriod.stream().map(PluggyClient.CardBill::id).collect(Collectors.toSet());
      var posted = new HashSet<String>();
      var postedWithBill = new HashSet<String>();
      var postedWithBillInPeriod = new HashSet<String>();
      var payments = new HashSet<String>();
      var pending = new HashSet<String>();
      var cursors = new HashSet<String>();
      var page = client.listTransactions(card.id(), from, to);
      while (true) {
        for (var transaction : page.results()) {
          if ("POSTED".equals(transaction.status())) {
            posted.add(transaction.id());
            var metadata = transaction.creditCardMetadata();
            if (metadata != null && metadata.billId() != null) {
              postedWithBill.add(transaction.id());
              if (billIds.contains(metadata.billId())) {
                postedWithBillInPeriod.add(transaction.id());
              }
            }
            if ("PAGAMENTO_FATURA".equals(transaction.operationType())
                || "PAGAMENTO".equals(transaction.operationType())) {
              payments.add(transaction.id());
            }
          } else if ("PENDING".equals(transaction.status())) {
            pending.add(transaction.id());
          }
        }
        if (page.next() == null) {
          break;
        }
        if (!cursors.add(page.next())) {
          throw new IllegalStateException("A Pluggy repetiu um cursor de transações do cartão.");
        }
        page = client.nextPage(page);
      }
      pending.removeAll(posted);
      out.printf(
          "Cartão %s | faturas disponíveis: %d | faturas com pagamentos: %d "
              + "| lançamentos efetivados: %d | lançamentos pendentes: %d%n",
          card.id(), bills.size(), billsWithPayments, posted.size(), pending.size());
      out.printf("  Faturas com vencimento no período: %d | efetivados ligados a alguma fatura: %d "
              + "| efetivados ligados a fatura do período: %d | pagamentos identificados no extrato do cartão: %d%n",
          billsInPeriod.size(), postedWithBill.size(), postedWithBillInPeriod.size(), payments.size());
    }
  }

  private static boolean dueDateInPeriod(String rawDate, LocalDate from, LocalDate to) {
    if (rawDate == null || rawDate.length() < 10) {
      return false;
    }
    LocalDate date = LocalDate.parse(rawDate.substring(0, 10));
    return !date.isBefore(from) && !date.isAfter(to);
  }
}
