package com.pedro.ledger.infrastructure.openfinance;

import com.pedro.ledger.infrastructure.web.account.AccountResponse;
import java.io.PrintStream;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public final class PluggyPreview {

  private static final int PREVIEW_LIMIT = 20;

  private PluggyPreview() {}

  public static void main(String[] args) {
    if (args.length == 0 || (args.length == 1 && args[0].equals("--help"))) {
      printUsage(System.out);
      return;
    }

    try {
      if (args.length == 2 && args[0].equals("finora-accounts")) {
        var local = new FinoraAccountPreview(parsePort(args[1]));
        for (AccountResponse account : local.activeAccounts()) {
          System.out.printf("%s | %s | %s%n", account.id(), terminalText(account.name()), account.type());
        }
        return;
      }
      Command command = parse(args);
      var client = new PluggyClient(
          System.getenv("PLUGGY_CLIENT_ID"), System.getenv("PLUGGY_CLIENT_SECRET")
      );
      command.run(client, System.out);
    } catch (IllegalArgumentException | IllegalStateException exception) {
      System.err.println(exception.getMessage());
      printUsage(System.err);
      System.exit(1);
    }
  }

  static Command parse(String[] args) {
    if (args.length >= 2 && args[0].equals("accounts")) {
      List<UUID> itemIds = Arrays.stream(args, 1, args.length)
          .map(PluggyPreview::parseId)
          .distinct()
          .toList();
      return (client, out) -> printAccounts(client, itemIds, out);
    }
    if (args.length == 2 && args[0].equals("diagnose")) {
      UUID itemId = parseId(args[1]);
      return (client, out) -> printDiagnosis(client, itemId, out);
    }
    if (args.length == 6 && args[0].equals("link-preview")) {
      int port = parsePort(args[1]);
      UUID finoraId = parseId(args[2]);
      UUID itemId = parseId(args[3]);
      UUID accountId = parseId(args[4]);
      LocalDate cutoff = parseDate(args[5]);
      return (client, out) -> printLinkPreview(
          client, new FinoraAccountPreview(port).activeAccounts(), finoraId, itemId, accountId, cutoff, out
      );
    }
    if (args.length == 4 && args[0].equals("card-preview")) {
      UUID itemId = parseId(args[1]);
      LocalDate from = parseDate(args[2]);
      LocalDate to = parseDate(args[3]);
      if (to.isBefore(from)) {
        throw new IllegalArgumentException("A data final deve ser igual ou posterior à inicial.");
      }
      return (client, out) -> PluggyCardPreview.print(client, itemId, from, to, out);
    }
    if (args.length == 5 && args[0].equals("transactions")) {
      UUID itemId = parseId(args[1]);
      UUID accountId = parseId(args[2]);
      LocalDate from = parseDate(args[3]);
      LocalDate to = parseDate(args[4]);
      if (to.isBefore(from)) {
        throw new IllegalArgumentException("A data final deve ser igual ou posterior à inicial.");
      }
      return (client, out) -> printTransactions(client, itemId, accountId, from, to, out);
    }
    throw new IllegalArgumentException("Comando ou argumentos inválidos.");
  }

  private static void printAccounts(PluggyClient client, List<UUID> itemIds, PrintStream out) {
    for (UUID itemId : itemIds) {
      var accounts = client.listBankAccounts(itemId);
      out.printf("Item %s (%d contas bancárias)%n", itemId, accounts.size());
      for (var account : accounts) {
        out.printf("  %s | %s | %s %s%n",
            account.id(), terminalText(account.name()), terminalText(account.currencyCode()),
            account.balance() == null ? "—" : account.balance().toPlainString());
      }
    }
  }

  static void printLinkPreview(
      PluggyClient client, List<AccountResponse> localAccounts, UUID finoraId, UUID itemId,
      UUID accountId, LocalDate cutoff, PrintStream out
  ) {
    var local = localAccounts.stream()
        .filter(account -> finoraId.equals(account.id()))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Conta ativa não encontrada no Finora."));
    var remote = requireBankAccount(client, itemId, accountId);

    out.printf("Finora: %s | %s%n", local.id(), terminalText(local.name()));
    out.printf("Pluggy: %s | %s | item %s%n", remote.id(), terminalText(remote.name()), itemId);
    out.printf("Primeiro dia elegível para importação: %s (inclusive)%n", cutoff);
    out.println("Prévia de vínculo. Nenhum vínculo ou lançamento foi gravado.");
  }

  private static void printDiagnosis(PluggyClient client, UUID itemId, PrintStream out) {
    var types = client.listAccountTypes(itemId);
    out.printf("Contas sem filtro: %d | Tipos: %s%n", types.size(),
        types.isEmpty() ? "—" : terminalText(String.join(", ", types)));
    var item = client.itemStatus(itemId);
    out.printf("Conector: %s%n", terminalText(item.connector() == null ? null : item.connector().name()));
    out.printf("Status: %s | Execução: %s%n",
        terminalText(item.status()), terminalText(item.executionStatus()));
    out.printf("Última sincronização: %s | Consentimento até: %s%n",
        item.lastUpdatedAt(), item.consentExpiresAt());
  }

  private static void printTransactions(
      PluggyClient client, UUID itemId, UUID accountId, LocalDate from, LocalDate to, PrintStream out
  ) {
    var account = requireBankAccount(client, itemId, accountId);

    var page = client.listTransactions(accountId, from, to);
    out.printf("Conta %s | %s a %s | datas em UTC%n", terminalText(account.name()), from, to);
    if (page.results().isEmpty()) {
      out.println("Nenhuma transação retornada no período.");
      return;
    }

    for (var transaction : page.results().stream().limit(PREVIEW_LIMIT).toList()) {
      out.printf("  %s | %s | %s %s | %s | %s%n", transaction.date(),
          terminalText(transaction.type()), terminalText(transaction.currencyCode()),
          transaction.amount() == null ? "—" : transaction.amount().toPlainString(),
          terminalText(transaction.status()), terminalText(transaction.description()));
    }
    if (page.results().size() > PREVIEW_LIMIT || page.next() != null) {
      out.printf("Há mais transações no período. A prévia mostra até %d registros.%n", PREVIEW_LIMIT);
    }
  }

  private static PluggyClient.BankAccount requireBankAccount(
      PluggyClient client, UUID itemId, UUID accountId
  ) {
    return client.listBankAccounts(itemId).stream()
        .filter(candidate -> accountId.equals(candidate.id()))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("A conta bancária não pertence ao item informado."));
  }

  private static int parsePort(String value) {
    try {
      int port = Integer.parseInt(value);
      if (port >= 1 && port <= 65535) {
        return port;
      }
    } catch (NumberFormatException ignored) {
      // Use the same message for nonnumeric and out-of-range values.
    }
    throw new IllegalArgumentException("Informe uma porta do Finora entre 1 e 65535.");
  }

  private static UUID parseId(String value) {
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("Os identificadores devem ser UUIDs.");
    }
  }

  private static LocalDate parseDate(String value) {
    try {
      return LocalDate.parse(value);
    } catch (DateTimeParseException exception) {
      throw new IllegalArgumentException("Informe as datas no formato AAAA-MM-DD.");
    }
  }

  private static String terminalText(String value) {
    return value == null ? "—" : value.replaceAll("[\\p{Cc}\\p{Cf}\\p{Zl}\\p{Zp}]", " ");
  }

  private static void printUsage(PrintStream out) {
    out.println("""
        Prévia local do Open Finance
          accounts <itemId> [itemId ...]
          diagnose <itemId>
          card-preview <itemId> <dataInicial> <dataFinal>
          finora-accounts <porta>
          link-preview <porta> <finoraId> <itemId> <accountId> <dataCorte>
          transactions <itemId> <accountId> <dataInicial> <dataFinal>

        Configure PLUGGY_CLIENT_ID e PLUGGY_CLIENT_SECRET no ambiente.
        As consultas não gravam dados no Finora.
        """);
  }

  @FunctionalInterface
  interface Command {
    void run(PluggyClient client, PrintStream out);
  }
}
