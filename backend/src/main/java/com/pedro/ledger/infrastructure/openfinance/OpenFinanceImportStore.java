package com.pedro.ledger.infrastructure.openfinance;

import com.pedro.ledger.domain.account.Account;
import com.pedro.ledger.domain.account.AccountRepository;
import com.pedro.ledger.domain.account.AccountSource;
import com.pedro.ledger.domain.account.AccountStatus;
import com.pedro.ledger.domain.transaction.Transaction;
import com.pedro.ledger.domain.transaction.TransactionRepository;
import com.pedro.ledger.domain.transaction.TransactionSource;
import com.pedro.ledger.domain.transaction.TransactionType;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OpenFinanceImportStore {

  private final AccountRepository accounts;
  private final TransactionRepository transactions;
  private final JdbcTemplate jdbc;
  private final EntityManager entities;

  public OpenFinanceImportStore(
      AccountRepository accounts, TransactionRepository transactions, JdbcTemplate jdbc,
      EntityManager entities
  ) {
    this.accounts = accounts;
    this.transactions = transactions;
    this.jdbc = jdbc;
    this.entities = entities;
  }

  @Transactional
  public ImportResult initialize(
      List<OpenFinanceInitialImport.AccountImport> imports,
      List<OpenFinanceInitialImport.TransferCandidate> transfers
  ) {
    var newAccounts = new HashMap<UUID, UUID>();
    for (var bank : imports) {
      var selection = bank.selection();
      List<Link> existing = jdbc.query("""
          SELECT item_id, cutoff_date FROM open_finance_account_links
          WHERE provider_account_id = ?
          """, (result, row) -> new Link(
              result.getObject("item_id", UUID.class), result.getObject("cutoff_date", LocalDate.class)
          ), selection.accountId());
      if (!existing.isEmpty()) {
        var link = existing.getFirst();
        if (!link.itemId().equals(selection.itemId()) || !link.cutoff().equals(bank.from())) {
          throw new IllegalStateException("O vínculo existente tem outro item ou data de corte.");
        }
        continue;
      }
      if (accounts.findAll().stream().anyMatch(account -> account.getName().equalsIgnoreCase(selection.name()))) {
        throw new IllegalStateException("Já existe no Finora uma conta com o nome escolhido.");
      }

      UUID accountId = UUID.randomUUID();
      accounts.save(Account.restore(accountId, selection.name(), bank.type(), AccountStatus.ACTIVE,
          bank.balance(), AccountSource.OPEN_FINANCE));
      entities.flush();
      jdbc.update("""
          INSERT INTO open_finance_account_links (account_id, item_id, provider_account_id, cutoff_date)
          VALUES (?, ?, ?, ?)
          """, accountId, selection.itemId(), selection.accountId(), bank.from());
      newAccounts.put(selection.accountId(), accountId);
    }

    var paired = new HashSet<OpenFinanceInitialImport.AccountPosting>();
    int saved = 0;
    for (var transfer : transfers) {
      var debit = transfer.debit();
      var credit = transfer.credit();
      UUID source = newAccounts.get(debit.account().accountId());
      UUID destination = newAccounts.get(credit.account().accountId());
      if (source == null && destination == null) {
        continue;
      }
      if (source == null || destination == null) {
        throw new IllegalStateException("A transferência envolve uma conta já importada. Nenhuma mudança foi gravada.");
      }
      var posting = debit.posting();
      var transaction = save(Transaction.create(posting.amount(), TransactionType.TRANSFER,
          posting.description(), posting.date(), TransactionSource.OPEN_FINANCE, source, destination, null));
      saveLink(transaction.getId(), source, posting.externalId());
      saveLink(transaction.getId(), destination, credit.posting().externalId());
      paired.add(debit);
      paired.add(credit);
      saved++;
    }

    for (var bank : imports) {
      UUID accountId = newAccounts.get(bank.selection().accountId());
      if (accountId == null) {
        continue;
      }
      for (var posting : bank.postings()) {
        if (paired.contains(new OpenFinanceInitialImport.AccountPosting(bank.selection(), posting))) {
          continue;
        }
        var transaction = save(Transaction.create(posting.amount(), posting.type(), posting.description(),
            posting.date(), TransactionSource.OPEN_FINANCE, accountId, null, null));
        saveLink(transaction.getId(), accountId, posting.externalId());
        saved++;
      }
    }
    return new ImportResult(newAccounts.size(), saved);
  }

  public List<LinkedBank> linkedBanks() {
    return jdbc.query("""
        SELECT a.id, a.name, l.item_id, l.provider_account_id, l.cutoff_date
        FROM open_finance_account_links l JOIN accounts a ON a.id = l.account_id
        WHERE a.status = 'ACTIVE' AND a.source = 'OPEN_FINANCE'
        ORDER BY a.id
        """, (row, index) -> new LinkedBank(
            row.getObject("id", UUID.class),
            new OpenFinanceInitialImport.Selection(row.getString("name"),
                row.getObject("item_id", UUID.class), row.getObject("provider_account_id", UUID.class)),
            row.getObject("cutoff_date", LocalDate.class)
        ));
  }

  @Transactional
  public SyncResult synchronize(List<OpenFinanceInitialImport.AccountImport> snapshots, Instant started) {
    if (!Boolean.TRUE.equals(jdbc.queryForObject(
        "SELECT pg_try_advisory_xact_lock(1086218321)", Boolean.class))) {
      return new SyncResult(0, 0);
    }

    var links = linkedBanks();
    if (links.size() != snapshots.size()) {
      throw new IllegalStateException("Os vínculos mudaram durante a sincronização.");
    }

    var localIds = new HashMap<UUID, UUID>();
    Set<OpenFinanceSyncPlan.ExternalPosting> imported = new HashSet<>();
    for (var bank : snapshots) {
      var link = links.stream().filter(candidate -> candidate.selection().equals(bank.selection())
          && candidate.cutoff().equals(bank.from())).findFirst()
          .orElseThrow(() -> new IllegalStateException("O vínculo mudou durante a sincronização."));
      UUID id = link.finoraId();
      int updated = jdbc.update("""
          UPDATE open_finance_account_links
          SET last_sync_started_at = ?
          WHERE account_id = ? AND (last_sync_started_at IS NULL OR last_sync_started_at < ?)
          """, java.sql.Timestamp.from(started), id, java.sql.Timestamp.from(started));
      if (updated != 1) {
        throw new IllegalStateException("Uma sincronização mais recente já foi gravada.");
      }
      var account = accounts.findByIdForUpdate(id)
          .orElseThrow(() -> new IllegalStateException("Conta vinculada não encontrada."));
      if (!account.isActive() || account.getSource() != AccountSource.OPEN_FINANCE
          || !account.getBalance().currency().equals(bank.balance().currency())) {
        throw new IllegalStateException("A conta vinculada mudou durante a sincronização.");
      }
      accounts.save(Account.restore(account.getId(), account.getName(), account.getType(),
          account.getStatus(), bank.balance(), account.getSource()));
      localIds.put(bank.selection().accountId(), id);
      imported.addAll(jdbc.query("""
          SELECT provider_transaction_id FROM open_finance_transaction_links WHERE account_id = ?
          """, (row, index) -> new OpenFinanceSyncPlan.ExternalPosting(
              bank.selection().accountId(), row.getString("provider_transaction_id")), id));
    }

    var plan = OpenFinanceSyncPlan.prepare(snapshots, imported);
    for (var entry : plan.newPostings()) {
      var posting = entry.posting();
      UUID accountId = localIds.get(entry.account().accountId());
      var transaction = save(Transaction.create(posting.amount(), posting.type(), posting.description(),
          posting.date(), TransactionSource.OPEN_FINANCE, accountId, null, null));
      saveLink(transaction.getId(), accountId, posting.externalId());
    }
    return new SyncResult(plan.newPostings().size(), plan.pendingPairs());
  }

  public record LinkedBank(UUID finoraId, OpenFinanceInitialImport.Selection selection, LocalDate cutoff) {}

  public record SyncResult(int imported, int pendingPairs) {}

  private Transaction save(Transaction transaction) {
    Transaction saved = transactions.save(transaction);
    entities.flush();
    return saved;
  }

  private void saveLink(UUID transactionId, UUID accountId, String externalId) {
    jdbc.update("""
        INSERT INTO open_finance_transaction_links (transaction_id, account_id, provider_transaction_id)
        VALUES (?, ?, ?)
        """, transactionId, accountId, externalId);
  }

  private record Link(UUID itemId, LocalDate cutoff) {}

  public record ImportResult(int accounts, int transactions) {}
}
