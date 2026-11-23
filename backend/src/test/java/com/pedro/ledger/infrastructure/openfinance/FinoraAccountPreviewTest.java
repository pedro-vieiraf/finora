package com.pedro.ledger.infrastructure.openfinance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class FinoraAccountPreviewTest {

  @Test
  void readsOnlyActiveAccountsThroughTheLocalApi() {
    RestClient.Builder builder = RestClient.builder();
    var server = MockRestServiceServer.bindTo(builder).build();
    server.expect(requestTo("http://127.0.0.1:8080/accounts"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess("""
            [
              {"id":"0d2f43f3-222f-4e88-b9ea-6c59895862c6","name":"Ativa",
               "type":"CHECKING","status":"ACTIVE","balance":100.01},
              {"id":"b15058f6-21aa-4b3f-ab37-eea355c3e158","name":"Inativa",
               "type":"CHECKING","status":"INACTIVE","balance":10}
            ]
            """, MediaType.APPLICATION_JSON));

    assertThat(new FinoraAccountPreview(builder, 8080).activeAccounts())
        .singleElement().satisfies(account -> {
          assertThat(account.name()).isEqualTo("Ativa");
          assertThat(account.balance()).isEqualByComparingTo("100.01");
        });
    server.verify();
  }
}
