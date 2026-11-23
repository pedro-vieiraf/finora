package com.pedro.ledger.infrastructure.openfinance;

import com.pedro.ledger.domain.account.AccountStatus;
import com.pedro.ledger.infrastructure.web.account.AccountResponse;
import java.util.Arrays;
import java.util.List;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

final class FinoraAccountPreview {

  private final RestClient http;

  FinoraAccountPreview(int port) {
    this(RestClient.builder().requestFactory(PluggyClient.requestFactory()), port);
  }

  FinoraAccountPreview(RestClient.Builder builder, int port) {
    this.http = builder.baseUrl("http://127.0.0.1:" + port).build();
  }

  List<AccountResponse> activeAccounts() {
    AccountResponse[] accounts;
    try {
      accounts = http.get().uri("/accounts").retrieve().body(AccountResponse[].class);
    } catch (RestClientException exception) {
      throw new IllegalStateException("Não foi possível consultar o Finora local. Confira o servidor e a porta.");
    }
    if (accounts == null) {
      throw new IllegalStateException("O Finora local retornou uma resposta vazia.");
    }
    return Arrays.stream(accounts)
        .filter(account -> account.status() == AccountStatus.ACTIVE)
        .toList();
  }
}
