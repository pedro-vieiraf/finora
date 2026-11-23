package com.pedro.ledger.infrastructure.openfinance;

import com.pedro.ledger.FinoraApplication;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.scheduling.config.TaskManagementConfigUtils;

public final class PluggyImportCommand {

  private PluggyImportCommand() {}

  public static void main(String[] args) {
    if (args.length == 0 || (args.length == 1 && "--help".equals(args[0]))) {
      System.out.println("""
          Primeira importação do Open Finance
            preview <dataInicial> <dataFinal> <nome> <itemId> <accountId> [...]
            confirm <dataInicial> <dataFinal> <nome> <itemId> <accountId> [...]
            review <dataInicial> <dataFinal> <valor> <nome> <itemId> <accountId> [...]

          Repita <nome> <itemId> <accountId> para cada conta bancária.
          Datas inclusivas em UTC, no formato AAAA-MM-DD.
          Após as contas, informe --transfer=origem,destino,dataDaSaída,valor ou
          --separate=origem,destino,dataDaSaída,valor para cada par revisado.
          Configure PLUGGY_CLIENT_ID e PLUGGY_CLIENT_SECRET no ambiente.
          O modo preview não inicia o Finora nem acessa o banco de dados.
          """);
      return;
    }

    try {
      var command = parse(args);
      var client = new PluggyClient(System.getenv("PLUGGY_CLIENT_ID"), System.getenv("PLUGGY_CLIENT_SECRET"));
      var importer = new OpenFinanceInitialImport(client);
      var imports = command.selections().stream()
          .map(selection -> importer.prepare(selection, command.from(), command.to()))
          .toList();
      if (command.reviewAmount() != null) {
        printAmountMatches(imports, command.reviewAmount());
        return;
      }

      for (var bank : imports) {
        var first = bank.postings().stream().map(OpenFinanceInitialImport.Posting::date).min(java.util.Comparator.naturalOrder());
        System.out.printf("%s | saldo %s %s | %d lançamentos efetivados | primeiro: %s%n",
            bank.selection().name(), bank.balance().currency().getCurrencyCode(),
            bank.balance().amount().toPlainString(), bank.postings().size(),
            first.map(value -> value.atOffset(ZoneOffset.UTC).toLocalDate().toString()).orElse("—"));
      }
      var candidates = OpenFinanceInitialImport.possibleInternalTransfers(imports);
      if (!candidates.isEmpty()) {
        System.out.printf("Possíveis transferências internas a revisar: %d%n", candidates.size());
        for (int index = 0; index < candidates.size(); index++) {
          var candidate = candidates.get(index);
          var debit = candidate.debit();
          var credit = candidate.credit();
          var amount = debit.posting().amount();
          System.out.printf(
              "  %d. %s %s | saída %s em %s | entrada %s em %s%n",
              index + 1, amount.currency().getCurrencyCode(), amount.amount().toPlainString(),
              debit.account().name(), debit.posting().date().atOffset(ZoneOffset.UTC),
              credit.account().name(), credit.posting().date().atOffset(ZoneOffset.UTC)
          );
        }
      }
      var transfers = command.confirm() || !command.decisions().isEmpty()
          ? OpenFinanceTransferReview.resolve(candidates, command.decisions()) : List.<OpenFinanceInitialImport.TransferCandidate>of();
      if (!command.decisions().isEmpty()) {
        System.out.printf("Revisão validada: %d transferências internas a gravar.%n", transfers.size());
      }
      if (!command.confirm()) {
        System.out.println("Prévia concluída. Nenhum dado foi gravado.");
        return;
      }

      try (var context = new SpringApplicationBuilder(FinoraApplication.class)
          .web(WebApplicationType.NONE)
          .initializers(application -> application.addBeanFactoryPostProcessor(factory -> {
            if (factory instanceof BeanDefinitionRegistry registry) {
              String scheduler = TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME;
              if (registry.containsBeanDefinition(scheduler)) {
                registry.removeBeanDefinition(scheduler);
              }
            }
          }))
          .run("--spring.sql.init.mode=never", "--spring.jpa.hibernate.ddl-auto=validate")) {
        var result = context.getBean(OpenFinanceImportStore.class).initialize(imports, transfers);
        System.out.printf("Importação concluída: %d contas e %d lançamentos gravados.%n",
            result.accounts(), result.transactions());
      }
    } catch (IllegalArgumentException | IllegalStateException exception) {
      System.err.println(exception.getMessage());
      System.exit(1);
    } catch (RuntimeException exception) {
      System.err.println("A importação falhou. Confira a configuração e o esquema do banco do Finora.");
      System.exit(1);
    }
  }

  private static void printAmountMatches(
      List<OpenFinanceInitialImport.AccountImport> imports, BigDecimal amount
  ) {
    for (var account : imports) {
      var matches = account.postings().stream()
          .filter(posting -> posting.amount().amount().compareTo(amount) == 0)
          .toList();
      System.out.printf("%s: %d registros efetivados desse valor%n",
          account.selection().name(), matches.size());
      for (var posting : matches) {
        System.out.printf("  %s | %s | %s %s%n", posting.date().atOffset(ZoneOffset.UTC),
            posting.type() == com.pedro.ledger.domain.transaction.TransactionType.INCOME
                ? "entrada" : "saída",
            posting.amount().currency().getCurrencyCode(), posting.amount().amount().toPlainString());
      }
    }
  }

  static Command parse(String[] args) {
    boolean review = args.length > 0 && "review".equals(args[0]);
    int firstAccount = review ? 4 : 3;
    int endAccounts = firstAccount;
    while (endAccounts < args.length && !args[endAccounts].startsWith("--")) {
      endAccounts += 3;
    }
    if (endAccounts > args.length || endAccounts < firstAccount + 3
        || (review && endAccounts != args.length)
        || (!review && !"preview".equals(args[0]) && !"confirm".equals(args[0]))) {
      throw new IllegalArgumentException("Argumentos inválidos. Use --help para consultar o comando.");
    }
    try {
      LocalDate from = LocalDate.parse(args[1]);
      LocalDate to = LocalDate.parse(args[2]);
      if (to.isBefore(from)) {
        throw new IllegalArgumentException("A data final deve ser igual ou posterior à inicial.");
      }
      BigDecimal reviewAmount = review ? new BigDecimal(args[3]) : null;
      if (reviewAmount != null && reviewAmount.signum() <= 0) {
        throw new IllegalArgumentException("Informe um valor positivo para revisar.");
      }

      var selections = new ArrayList<OpenFinanceInitialImport.Selection>();
      var names = new HashSet<String>();
      var accountIds = new HashSet<UUID>();
      for (int index = firstAccount; index < endAccounts; index += 3) {
        String name = args[index].trim();
        UUID itemId = UUID.fromString(args[index + 1]);
        UUID accountId = UUID.fromString(args[index + 2]);
        if (name.isBlank() || name.codePoints().anyMatch(Character::isISOControl)
            || !names.add(name.toLowerCase(Locale.ROOT)) || !accountIds.add(accountId)) {
          throw new IllegalArgumentException("Informe nomes e contas bancárias distintos e válidos.");
        }
        selections.add(new OpenFinanceInitialImport.Selection(name, itemId, accountId));
      }
      var decisions = new ArrayList<OpenFinanceTransferReview.Decision>();
      for (int index = endAccounts; index < args.length; index++) {
        decisions.add(OpenFinanceTransferReview.Decision.parse(args[index]));
      }
      return new Command("confirm".equals(args[0]), from, to, reviewAmount,
          List.copyOf(selections), List.copyOf(decisions));
    } catch (DateTimeParseException exception) {
      throw new IllegalArgumentException("Informe as datas no formato AAAA-MM-DD.");
    }
  }

  record Command(boolean confirm, LocalDate from, LocalDate to, BigDecimal reviewAmount,
                 List<OpenFinanceInitialImport.Selection> selections,
                 List<OpenFinanceTransferReview.Decision> decisions) {}
}
