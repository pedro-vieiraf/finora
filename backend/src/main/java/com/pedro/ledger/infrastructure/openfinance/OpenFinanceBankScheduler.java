package com.pedro.ledger.infrastructure.openfinance;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "ledger.open-finance", name = "scheduler-enabled", havingValue = "true")
public class OpenFinanceBankScheduler {

  private static final Logger LOGGER = LoggerFactory.getLogger(OpenFinanceBankScheduler.class);

  private final OpenFinanceImportStore store;
  private final PluggyClient client;
  private final OpenFinanceInitialImport reader;
  private final Clock clock;

  public OpenFinanceBankScheduler(
      OpenFinanceImportStore store, Clock clock,
      @Value("${PLUGGY_CLIENT_ID:}") String clientId,
      @Value("${PLUGGY_CLIENT_SECRET:}") String clientSecret
  ) {
    this.store = store;
    this.clock = clock;
    this.client = clientId.isBlank() || clientSecret.isBlank()
        ? null : new PluggyClient(clientId, clientSecret);
    this.reader = client == null ? null : new OpenFinanceInitialImport(client);
  }

  @Scheduled(
      fixedDelayString = "${ledger.open-finance.scheduler-delay-ms:21600000}",
      initialDelayString = "${ledger.open-finance.scheduler-initial-delay-ms:60000}"
  )
  public void synchronize() {
    if (client == null) {
      LOGGER.warn("Sincronização Open Finance habilitada, mas as credenciais não estão configuradas.");
      return;
    }
    try {
      var started = clock.instant();
      LocalDate today = started.atOffset(ZoneOffset.UTC).toLocalDate();
      var snapshots = store.linkedBanks().stream().map(link -> {
        var status = client.itemStatus(link.selection().itemId());
        if (!"UPDATED".equals(status.status()) || !"SUCCESS".equals(status.executionStatus())
            || (status.consentExpiresAt() != null && !status.consentExpiresAt().isAfter(started))) {
          throw new IllegalStateException("A Pluggy ainda não tem uma atualização completa e autorizada.");
        }
        return reader.prepare(link.selection(), link.cutoff(), today);
      }).toList();

      if (!snapshots.isEmpty()) {
        var result = store.synchronize(snapshots, started);
        LOGGER.info("Open Finance: {} lançamentos novos, {} possíveis pares pendentes.",
            result.imported(), result.pendingPairs());
      }
    } catch (RuntimeException exception) {
      LOGGER.warn("A sincronização bancária Open Finance falhou. Será tentada novamente.");
    }
  }
}
