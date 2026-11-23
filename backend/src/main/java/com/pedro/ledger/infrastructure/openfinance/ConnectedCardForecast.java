package com.pedro.ledger.infrastructure.openfinance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.List;
import java.util.TreeMap;

final class ConnectedCardForecast {

  private ConnectedCardForecast() {}

  static List<ConnectedCardBills.Forecast> withInstallmentEstimates(
      List<ConnectedCardBills.Forecast> reported, ConnectedCardBills.Bill latest,
      YearMonth firstMonth, YearMonth lastMonth, LocalDate today,
      Collection<PluggyClient.Transaction> transactions
  ) {
    if (latest == null || latest.currency() == null) {
      return reported;
    }

    var previousMonth = YearMonth.from(latest.dueDate());
    var estimates = new TreeMap<YearMonth, BigDecimal>();
    for (var entry : latest.entries()) {
      if (!"DEBIT".equals(entry.direction()) || entry.amount() == null
          || !latest.currency().equals(entry.currency())
          || entry.installmentNumber() == null || entry.totalInstallments() == null
          || entry.installmentNumber() < 1 || entry.installmentNumber() >= entry.totalInstallments()) {
        continue;
      }

      int remaining = entry.totalInstallments() - entry.installmentNumber();
      for (int offset = 1; offset <= remaining; offset++) {
        YearMonth month = previousMonth.plusMonths(offset);
        if (month.isAfter(lastMonth)) {
          break;
        }
        if (!month.isBefore(firstMonth)) {
          estimates.merge(month, entry.amount().abs(), BigDecimal::add);
        }
      }
    }

    var nextCycle = previousMonth.plusMonths(1);
    if (nextCycle.equals(firstMonth) && latest.closingDate() != null
        && today.isBefore(latest.closingDate().plusMonths(1))) {
      for (var entry : transactions) {
        if (isRecentUnbilledSingleCharge(entry, latest, today)) {
          estimates.merge(nextCycle, entry.amount().abs(), BigDecimal::add);
        }
      }
    }

    var forecasts = new TreeMap<String, ConnectedCardBills.Forecast>();
    for (var forecast : reported) {
      forecasts.put(forecast.month(), forecast);
    }
    estimates.forEach((month, total) -> forecasts.putIfAbsent(month.toString(),
        new ConnectedCardBills.Forecast(month.toString(), total, latest.currency(), true)));
    return List.copyOf(forecasts.values());
  }

  private static boolean isRecentUnbilledSingleCharge(
      PluggyClient.Transaction entry, ConnectedCardBills.Bill latest, LocalDate today
  ) {
    var metadata = entry.creditCardMetadata();
    if (metadata == null || metadata.billId() != null || metadata.billForecastDate() != null
        || entry.date() == null || entry.amount() == null || !latest.currency().equals(entry.currencyCode())
        || !"PENDING".equals(entry.status()) || !"DEBIT".equals(entry.type())
        || "PAGAMENTO".equals(entry.operationType()) || "PAGAMENTO_FATURA".equals(entry.operationType())
        || (!"SINGLE".equals(metadata.paymentType()) && !Integer.valueOf(1).equals(metadata.totalInstallments()))) {
      return false;
    }
    var date = entry.date().atOffset(ZoneOffset.UTC).toLocalDate();
    return date.isAfter(latest.closingDate()) && !date.isAfter(today);
  }

  static List<ConnectedCardBills.Forecast> from(
      Collection<PluggyClient.Transaction> transactions, String currency, YearMonth firstMonth,
      YearMonth lastMonth
  ) {
    if (currency == null) {
      return List.of();
    }
    var totals = new TreeMap<YearMonth, BigDecimal>();
    for (var transaction : transactions) {
      var metadata = transaction.creditCardMetadata();
      if (metadata == null || metadata.billId() != null || metadata.billForecastDate() == null
          || "PAGAMENTO".equals(transaction.operationType())
          || "PAGAMENTO_FATURA".equals(transaction.operationType())
          || (!"PENDING".equals(transaction.status()) && !"POSTED".equals(transaction.status()))) {
        continue;
      }

      YearMonth month;
      try {
        month = YearMonth.parse(metadata.billForecastDate());
      } catch (DateTimeParseException exception) {
        continue;
      }
      if (month.isBefore(firstMonth) || month.isAfter(lastMonth)) {
        continue;
      }

      var amount = currency.equals(transaction.currencyCode())
          ? transaction.amount() : transaction.amountInAccountCurrency();
      if (amount == null) {
        continue;
      }
      if ("DEBIT".equals(transaction.type())) {
        totals.merge(month, amount.abs(), BigDecimal::add);
      } else if ("CREDIT".equals(transaction.type())) {
        totals.merge(month, amount.abs().negate(), BigDecimal::add);
      }
    }
    return totals.entrySet().stream().map(entry -> new ConnectedCardBills.Forecast(
        entry.getKey().toString(), entry.getValue().max(BigDecimal.ZERO), currency, false)).toList();
  }
}
