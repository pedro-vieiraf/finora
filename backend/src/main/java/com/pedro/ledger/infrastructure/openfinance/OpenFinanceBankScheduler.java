package com.pedro.ledger.infrastructure.openfinance;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
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
      for (var link : store.linkedBanks()) {
        try {
          var status = client.itemStatus(link.selection().itemId());
          if (("SUCCESS".equals(status.executionStatus()) || "PARTIAL_SUCCESS".equals(status.executionStatus()))
              && "UPDATED".equals(status.status())
              && (status.consentExpiresAt() == null || status.consentExpiresAt().isAfter(started))) {
            var snapshot = reader.prepare(link.selection(), link.cutoff(), today);
            var result = store.synchronize(List.of(snapshot), started);
            LOGGER.info("Open Finance: {} lançamentos novos na conexão.", result.imported());
          } else {
            LOGGER.warn("A conexão bancária ainda não está pronta para sincronizar.");
          }
        } catch (RuntimeException exception) {
          LOGGER.warn("Uma conexão bancária falhou e será tentada novamente.");
        }
      }

    } catch (RuntimeException exception) {
      LOGGER.warn("A sincronização bancária Open Finance falhou. Será tentada novamente.");
    }
  }
}
