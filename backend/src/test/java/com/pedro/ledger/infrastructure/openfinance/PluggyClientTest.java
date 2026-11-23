package com.pedro.ledger.infrastructure.openfinance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class PluggyClientTest {

  private static final String BASE_URL = "https://pluggy.example";
  private static final UUID ITEM_ID = UUID.fromString("b15058f6-21aa-4b3f-ab37-eea355c3e158");
  private static final UUID ACCOUNT_ID = UUID.fromString("0d2f43f3-222f-4e88-b9ea-6c59895862c6");

  private MockRestServiceServer server;
  private PluggyClient client;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder();
    server = MockRestServiceServer.bindTo(builder).build();
    client = new PluggyClient(builder, URI.create(BASE_URL), "test-id", "test-secret");
  }

  @Test
  void readsBankAccountsWithoutRoundingBalancesAndReusesTheApiKey() {
    expectAuthentication();
    String url = BASE_URL + "/accounts?itemId=" + ITEM_ID + "&type=BANK";
    server.expect(once(), requestTo(url))
        .andExpect(method(HttpMethod.GET))
        .andExpect(header("X-API-KEY", "test-api-key"))
        .andRespond(withSuccess("""
            {"results":[{"id":"%s","itemId":"%s","name":"Conta corrente",
                "currencyCode":"BRL","balance":123456789012345.67,"number":"private"}]}
            """.formatted(ACCOUNT_ID, ITEM_ID), MediaType.APPLICATION_JSON));
    server.expect(once(), requestTo(url))
        .andExpect(header("X-API-KEY", "test-api-key"))
        .andRespond(withSuccess("{\"results\":[]}", MediaType.APPLICATION_JSON));

    assertThat(client.listBankAccounts(ITEM_ID)).singleElement().satisfies(account -> {
      assertThat(account.id()).isEqualTo(ACCOUNT_ID);
      assertThat(account.balance()).isEqualByComparingTo("123456789012345.67");
    });
    assertThat(client.listBankAccounts(ITEM_ID)).isEmpty();
    server.verify();
  }

  @Test
  void readsItemStatusAndOnlyAccountTypesWithoutTheBankFilter() {
    expectAuthentication();
    server.expect(requestTo(BASE_URL + "/items/" + ITEM_ID))
        .andExpect(header("X-API-KEY", "test-api-key"))
        .andRespond(withSuccess("""
            {"status":"UPDATED","executionStatus":"SUCCESS","connector":{"name":"MeuPluggy"}}
            """, MediaType.APPLICATION_JSON));
    server.expect(requestTo(BASE_URL + "/accounts?itemId=" + ITEM_ID))
        .andExpect(header("X-API-KEY", "test-api-key"))
        .andRespond(withSuccess("""
            {"results":[{"type":"CREDIT","number":"private"}]}
            """, MediaType.APPLICATION_JSON));

    assertThat(client.itemStatus(ITEM_ID).connector().name()).isEqualTo("MeuPluggy");
    assertThat(client.listAccountTypes(ITEM_ID)).containsExactly("CREDIT");
    server.verify();
  }

  @Test
  void readsDatedTransactionsAndPreservesTheEncodedNextQuery() {
    expectAuthentication();
    String path = BASE_URL + "/v2/transactions";
    String firstQuery = "?accountId=" + ACCOUNT_ID + "&dateFrom=2026-11-01&dateTo=2026-11-21";
    String next = firstQuery + "&after=abc%2B%2F%3D";
    server.expect(requestTo(path + firstQuery))
        .andExpect(header("X-API-KEY", "test-api-key"))
        .andRespond(withSuccess("""
            {"results":[{"id":"external-id","accountId":"%s","description":"Compra",
                "date":"2026-11-10T12:00:00Z","amount":-10.25,"currencyCode":"BRL",
                "type":"DEBIT","status":"POSTED","creditCardMetadata":{"billId":"bill1","installmentNumber":1,
                "totalInstallments":2}}],"next":"%s"}
            """.formatted(ACCOUNT_ID, next), MediaType.APPLICATION_JSON));
    server.expect(requestTo(path + next))
        .andExpect(header("X-API-KEY", "test-api-key"))
        .andRespond(withSuccess("{\"results\":[],\"next\":null}", MediaType.APPLICATION_JSON));

    var page = client.listTransactions(
        ACCOUNT_ID, LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-21")
    );

    assertThat(page.results()).singleElement().satisfies(transaction -> {
      assertThat(transaction.amount()).isEqualByComparingTo("-10.25");
      assertThat(transaction.type()).isEqualTo("DEBIT");
      assertThat(transaction.status()).isEqualTo("POSTED");
      assertThat(transaction.creditCardMetadata().billId()).isEqualTo("bill1");
      assertThat(transaction.creditCardMetadata().totalInstallments()).isEqualTo(2);
    });
    assertThat(client.nextPage(page).next()).isNull();
    server.verify();
  }

  @Test
  void rejectsInvalidDateRangesAndAbsolutePaginationUrls() {
    LocalDate date = LocalDate.parse("2026-11-21");
    assertThatThrownBy(() -> client.listTransactions(ACCOUNT_ID, date, date.minusDays(1)))
        .isInstanceOf(IllegalArgumentException.class);
    var page = new PluggyClient.TransactionPage(List.of(), "https://another.example");
    assertThatThrownBy(() -> client.nextPage(page)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void authenticationErrorsDoNotExposeTheResponseBody() {
    server.expect(requestTo(BASE_URL + "/auth"))
        .andRespond(withBadRequest().body("sensitive upstream error"));

    assertThatThrownBy(() -> client.listBankAccounts(ITEM_ID))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Autenticação da Pluggy: HTTP 400")
        .hasNoCause();
    server.verify();
  }

  @Test
  void reportsHttpStatusWithoutExposingTheUpstreamBody() {
    expectAuthentication();
    server.expect(requestTo(BASE_URL + "/items/" + ITEM_ID))
        .andRespond(withBadRequest().body("private upstream details"));

    assertThatThrownBy(() -> client.itemStatus(ITEM_ID))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Pluggy returned HTTP 400")
        .hasNoCause();
    server.verify();
  }

  @Test
  void readsCreditCardsAndTheirBillsAcrossPages() {
    expectAuthentication();
    server.expect(requestTo(BASE_URL + "/accounts?itemId=" + ITEM_ID + "&type=CREDIT"))
        .andRespond(withSuccess("""
            {"results":[{"id":"%s","subtype":"CREDIT_CARD"}]}
            """.formatted(ACCOUNT_ID), MediaType.APPLICATION_JSON));
    server.expect(requestTo(BASE_URL + "/bills?accountId=" + ACCOUNT_ID + "&page=1"))
        .andRespond(withSuccess("""
            {"page":1,"totalPages":2,"results":[{"id":"bill1","totalAmount":10.25,"payments":[]}]}
            """, MediaType.APPLICATION_JSON));
    server.expect(requestTo(BASE_URL + "/bills?accountId=" + ACCOUNT_ID + "&page=2"))
        .andRespond(withSuccess("""
            {"page":2,"totalPages":2,"results":[{"id":"bill2","payments":[{"id":"paid","amount":10.25}]}]}
            """, MediaType.APPLICATION_JSON));

    assertThat(client.listCreditCards(ITEM_ID)).singleElement()
        .satisfies(card -> assertThat(card.id()).isEqualTo(ACCOUNT_ID));
    var bills = client.listCardBills(ACCOUNT_ID);
    assertThat(bills).hasSize(2);
    assertThat(bills.getLast().payments()).singleElement()
        .satisfies(payment -> assertThat(payment.amount()).isEqualByComparingTo("10.25"));
    server.verify();
  }

  private void expectAuthentication() {
    server.expect(once(), requestTo(BASE_URL + "/auth"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().json("""
            {"clientId":"test-id","clientSecret":"test-secret"}
            """))
        .andRespond(withSuccess("{\"apiKey\":\"test-api-key\"}", MediaType.APPLICATION_JSON));
  }
}
