package com.pedro.ledger.infrastructure.openfinance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
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
    var estimates = new TreeMap<YearMonth, List<ConnectedCardBills.Entry>>();
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
          estimates.computeIfAbsent(month, key -> new ArrayList<>()).add(
              new ConnectedCardBills.Entry(entry.id(), entry.date(), entry.description(), entry.direction(),
                  entry.amount().abs(), entry.currency(), entry.installmentNumber() + offset,
                  entry.totalInstallments()));
        }
      }
    }

    var nextCycle = previousMonth.plusMonths(1);
    if (nextCycle.equals(firstMonth) && latest.closingDate() != null
        && today.isBefore(latest.closingDate().plusMonths(1))) {
      for (var entry : transactions) {
        if (isRecentUnbilledSingleCharge(entry, latest, today)) {
          estimates.computeIfAbsent(nextCycle, key -> new ArrayList<>()).add(
              forecastEntry(entry, entry.amount().abs(), latest.currency()));
        }
      }
    }

    var forecasts = new TreeMap<String, ConnectedCardBills.Forecast>();
    for (var forecast : reported) {
      forecasts.put(forecast.month(), forecast);
    }
    estimates.forEach((month, entries) -> forecasts.putIfAbsent(month.toString(),
        forecast(month, latest.currency(), true, entries)));
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
    var entriesByMonth = new TreeMap<YearMonth, List<ConnectedCardBills.Entry>>();
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
      if ("DEBIT".equals(transaction.type()) || "CREDIT".equals(transaction.type())) {
        var signedAmount = "DEBIT".equals(transaction.type()) ? amount.abs() : amount.abs().negate();
        entriesByMonth.computeIfAbsent(month, key -> new ArrayList<>())
            .add(forecastEntry(transaction, signedAmount, currency));
      }
    }
    return entriesByMonth.entrySet().stream()
        .map(entry -> forecast(entry.getKey(), currency, false, entry.getValue())).toList();
  }

  private static ConnectedCardBills.Entry forecastEntry(
      PluggyClient.Transaction transaction, BigDecimal amount, String currency
  ) {
    var metadata = transaction.creditCardMetadata();
    return new ConnectedCardBills.Entry(transaction.id(), transaction.date(),
        transaction.description(), transaction.type(), amount, currency,
        metadata.installmentNumber(), metadata.totalInstallments());
  }

  private static ConnectedCardBills.Forecast forecast(
      YearMonth month, String currency, boolean estimated, List<ConnectedCardBills.Entry> entries
  ) {
    var total = entries.stream().map(ConnectedCardBills.Entry::amount)
        .reduce(BigDecimal.ZERO, BigDecimal::add).max(BigDecimal.ZERO);
    return new ConnectedCardBills.Forecast(month.toString(), total, currency, estimated, List.copyOf(entries));
  }
}
