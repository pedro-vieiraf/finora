package com.pedro.ledger.infrastructure.openfinance;

import com.pedro.ledger.domain.account.AccountType;
import com.pedro.ledger.domain.money.Money;
import com.pedro.ledger.domain.transaction.TransactionType;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class OpenFinanceInitialImport {

  private final PluggyClient client;

  OpenFinanceInitialImport(PluggyClient client) {
    this.client = client;
  }

  AccountImport prepare(Selection selection, LocalDate from, LocalDate to) {
    var account = client.listBankAccounts(selection.itemId()).stream()
        .filter(candidate -> selection.accountId().equals(candidate.id()))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("A conta bancária não pertence ao item selecionado."));

    AccountType type = switch (account.subtype() == null ? "" : account.subtype()) {
      case "CHECKING_ACCOUNT" -> AccountType.CHECKING;
      case "SAVINGS_ACCOUNT" -> AccountType.SAVINGS;
      default -> throw new IllegalArgumentException("Tipo de conta bancária ainda não suportado.");
    };
    if (account.balance() == null || account.currencyCode() == null) {
      throw new IllegalStateException("A Pluggy não retornou saldo e moeda para a conta.");
    }
    Currency currency = Currency.getInstance(account.currencyCode());
    Money balance = Money.of(account.balance(), currency);

    Map<String, Posting> postings = new LinkedHashMap<>();
    Map<String, Posting> pendingDebits = new LinkedHashMap<>();
    Set<String> cursors = new HashSet<>();
    var page = client.listTransactions(selection.accountId(), from, to);
    while (true) {
      for (var transaction : page.results()) {
        boolean posted = "POSTED".equals(transaction.status());
        boolean pendingDebit = "PENDING".equals(transaction.status()) && "DEBIT".equals(transaction.type());
        if (!posted && !pendingDebit) {
          continue;
        }
        Posting posting;
        try {
          posting = toPosting(transaction, selection, currency);
        } catch (IllegalArgumentException | IllegalStateException exception) {
          if (pendingDebit) {
            // An incomplete bank authorization must not block confirmed transactions.
            continue;
          }
          throw exception;
        }
        LocalDate date = posting.date().atOffset(ZoneOffset.UTC).toLocalDate();
        if (date.isBefore(from) || date.isAfter(to)) {
          continue;
        }
        if (pendingDebit) {
          pendingDebits.putIfAbsent(posting.externalId(), posting);
          continue;
        }
        var previous = postings.putIfAbsent(posting.externalId(), posting);
        if (previous != null && !previous.equals(posting)) {
          throw new IllegalStateException("A Pluggy retornou versões diferentes do mesmo lançamento.");
        }
      }
      if (page.next() == null) {
        break;
      }
      if (!cursors.add(page.next())) {
        throw new IllegalStateException("A Pluggy repetiu um cursor da paginação.");
      }
      page = client.nextPage(page);
    }

    pendingDebits.keySet().removeAll(postings.keySet());
    return new AccountImport(selection, type, balance, from, List.copyOf(postings.values()),
        List.copyOf(pendingDebits.values()));
  }

  static List<TransferCandidate> possibleInternalTransfers(List<AccountImport> imports) {
    Map<TransferKey, List<AccountPosting>> credits = new LinkedHashMap<>();
    for (var account : imports) {
      for (var posting : account.postings()) {
        if (posting.type() == TransactionType.INCOME) {
          var date = posting.date().atOffset(ZoneOffset.UTC).toLocalDate();
          credits.computeIfAbsent(new TransferKey(date, posting.amount()), key -> new ArrayList<>())
              .add(new AccountPosting(account.selection(), posting));
        }
      }
    }
    List<TransferCandidate> candidates = new ArrayList<>();
    for (var account : imports) {
      for (var posting : account.postings()) {
        if (posting.type() != TransactionType.EXPENSE) {
          continue;
        }
        var date = posting.date().atOffset(ZoneOffset.UTC).toLocalDate();
        for (int offset = -1; offset <= 1; offset++) {
          var matching = credits.getOrDefault(
              new TransferKey(date.plusDays(offset), posting.amount()), List.of());
          for (var credit : matching) {
            if (!credit.account().accountId().equals(account.selection().accountId())) {
              candidates.add(new TransferCandidate(new AccountPosting(account.selection(), posting), credit));
            }
          }
        }
      }
    }
    return List.copyOf(candidates);
  }

  record AccountPosting(Selection account, Posting posting) {}

  record TransferCandidate(AccountPosting debit, AccountPosting credit) {}

  private record TransferKey(LocalDate date, Money amount) {}

  private static Posting toPosting(
      PluggyClient.Transaction transaction, Selection selection, Currency accountCurrency
  ) {
    if (!selection.accountId().equals(transaction.accountId())
        || transaction.id() == null || transaction.id().isBlank() || transaction.date() == null) {
      throw new IllegalStateException("A Pluggy retornou um lançamento sem identidade ou data válida.");
    }
    var amount = accountCurrency.getCurrencyCode().equals(transaction.currencyCode())
        ? transaction.amount()
        : transaction.amountInAccountCurrency();
    if (amount == null || amount.signum() == 0) {
      throw new IllegalStateException("A Pluggy retornou um lançamento sem valor na moeda da conta.");
    }
    TransactionType type = switch (transaction.type() == null ? "" : transaction.type()) {
      case "CREDIT" -> TransactionType.INCOME;
      case "DEBIT" -> TransactionType.EXPENSE;
      default -> throw new IllegalStateException("A Pluggy retornou uma direção de lançamento desconhecida.");
    };
    return new Posting(transaction.id(), type, Money.of(amount.abs(), accountCurrency),
        transaction.description(), transaction.date());
  }

  record Selection(String name, UUID itemId, UUID accountId) {}

  record AccountImport(
      Selection selection, AccountType type, Money balance, LocalDate from, List<Posting> postings,
      List<Posting> pendingDebits
  ) {
    AccountImport(Selection selection, AccountType type, Money balance, LocalDate from,
        List<Posting> postings) {
      this(selection, type, balance, from, postings, List.of());
    }
  }

  record Posting(
      String externalId, TransactionType type, Money amount, String description, Instant date
  ) {}
}
