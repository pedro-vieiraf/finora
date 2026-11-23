package com.pedro.ledger.infrastructure.openfinance;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

public final class PluggyClient {

  private static final URI API_URL = URI.create("https://api.pluggy.ai");
  private static final String API_KEY_HEADER = "X-API-KEY";
  private static final String TRANSACTIONS_PATH = "/v2/transactions";
  private static final Duration API_KEY_REFRESH_INTERVAL = Duration.ofMinutes(110);

  private final RestClient http;
  private final URI baseUrl;
  private final Credentials credentials;
  private String apiKey;
  private Instant refreshAfter = Instant.MIN;

  public PluggyClient(String clientId, String clientSecret) {
    this(RestClient.builder().requestFactory(requestFactory()), API_URL, clientId, clientSecret);
  }

  PluggyClient(RestClient.Builder builder, URI baseUrl, String clientId, String clientSecret) {
    if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
      throw new IllegalArgumentException("Pluggy client ID and secret are required");
    }
    this.http = builder.baseUrl(baseUrl.toString()).build();
    this.baseUrl = baseUrl;
    this.credentials = new Credentials(clientId, clientSecret);
  }

  ItemStatus itemStatus(UUID itemId) {
    return get(baseUrl.resolve("/items/" + Objects.requireNonNull(itemId)),
        new ParameterizedTypeReference<ItemStatus>() {});
  }

  List<String> listAccountTypes(UUID itemId) {
    AccountTypes response = get(
        baseUrl.resolve("/accounts?itemId=" + Objects.requireNonNull(itemId)),
        new ParameterizedTypeReference<AccountTypes>() {}
    );
    return response.results().stream()
        .map(account -> Objects.toString(account.type(), "UNKNOWN"))
        .toList();
  }

  public List<BankAccount> listBankAccounts(UUID itemId) {
    Objects.requireNonNull(itemId, "itemId");
    return get(
        baseUrl.resolve("/accounts?itemId=" + itemId + "&type=BANK"),
        new ParameterizedTypeReference<AccountResponse>() {}
    ).results();
  }

  List<CreditAccount> listCreditCards(UUID itemId) {
    CreditAccounts accounts = get(baseUrl.resolve("/accounts?itemId=" + itemId + "&type=CREDIT"),
        new ParameterizedTypeReference<CreditAccounts>() {});
    return accounts.results().stream()
        .filter(account -> "CREDIT_CARD".equals(account.subtype()))
        .toList();
  }

  List<CardBill> listCardBills(UUID accountId) {
    List<CardBill> bills = new ArrayList<>();
    int page = 1;
    while (true) {
      BillPage result = get(baseUrl.resolve("/bills?accountId=" + accountId + "&page=" + page),
          new ParameterizedTypeReference<BillPage>() {});
      bills.addAll(result.results());
      if (page >= result.totalPages()) {
        return List.copyOf(bills);
      }
      if (result.page() != page) {
        throw new IllegalStateException("A Pluggy retornou uma página de faturas inválida.");
      }
      page++;
    }
  }

  public TransactionPage listTransactions(UUID accountId, LocalDate from, LocalDate to) {
    Objects.requireNonNull(accountId, "accountId");
    Objects.requireNonNull(from, "from");
    Objects.requireNonNull(to, "to");
    if (to.isBefore(from)) {
      throw new IllegalArgumentException("Transaction end date must be on or after the start date");
    }
    return transactionPage(baseUrl.resolve(
        TRANSACTIONS_PATH + "?accountId=" + accountId + "&dateFrom=" + from + "&dateTo=" + to
    ));
  }

  public TransactionPage nextPage(TransactionPage currentPage) {
    String next = currentPage.next();
    if (next == null || !next.startsWith("?") || next.contains("#")) {
      throw new IllegalArgumentException("A Pluggy next-page query is required");
    }
    return transactionPage(baseUrl.resolve(TRANSACTIONS_PATH + next));
  }

  private TransactionPage transactionPage(URI uri) {
    return get(uri, new ParameterizedTypeReference<TransactionPage>() {});
  }

  private <T> T get(URI uri, ParameterizedTypeReference<T> type) {
    String key = authenticate();
    try {
      return requireBody(http.get()
          .uri(uri)
          .header(API_KEY_HEADER, key)
          .retrieve()
          .body(type));
    } catch (RestClientResponseException exception) {
      throw new IllegalStateException("Pluggy returned HTTP " + exception.getStatusCode().value());
    } catch (ResourceAccessException exception) {
      throw new IllegalStateException("Could not connect to Pluggy or the request timed out");
    } catch (RestClientException exception) {
      throw new IllegalStateException("Could not decode the response from Pluggy");
    }
  }

  private synchronized String authenticate() {
    Instant now = Instant.now();
    if (now.isBefore(refreshAfter)) {
      return apiKey;
    }

    try {
      AuthenticationResponse response = requireBody(http.post()
          .uri("/auth")
          .contentType(MediaType.APPLICATION_JSON)
          .body(credentials)
          .retrieve()
          .body(AuthenticationResponse.class));
      if (response.apiKey() == null || response.apiKey().isBlank()) {
        throw new IllegalStateException("Pluggy returned an empty API key");
      }
      apiKey = response.apiKey();
      refreshAfter = now.plus(API_KEY_REFRESH_INTERVAL);
      return apiKey;
    } catch (RestClientResponseException exception) {
      throw new IllegalStateException("Autenticação da Pluggy: HTTP " + exception.getStatusCode().value());
    } catch (ResourceAccessException exception) {
      throw new IllegalStateException("Autenticação da Pluggy: falha de conexão ou tempo limite.");
    } catch (RestClientException exception) {
      throw new IllegalStateException("Autenticação da Pluggy: resposta em formato inesperado.");
    }
  }

  private static <T> T requireBody(T body) {
    if (body == null) {
      throw new IllegalStateException("Pluggy returned an empty response");
    }
    return body;
  }

  static JdkClientHttpRequestFactory requestFactory() {
    HttpClient client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();
    var factory = new JdkClientHttpRequestFactory(client);
    factory.setReadTimeout(Duration.ofSeconds(15));
    return factory;
  }

  record ItemStatus(
      String status, String executionStatus, Instant lastUpdatedAt, Instant consentExpiresAt,
      Connector connector
  ) {}

  record Connector(String name) {}

  record CreditAccount(
      UUID id, String subtype, String currencyCode, BigDecimal balance, CreditData creditData
  ) {}

  record CreditData(String balanceDueDate) {}

  record CardBill(
      String id, String dueDate, String billClosingDate, BigDecimal totalAmount,
      String totalAmountCurrencyCode, List<BillPayment> payments
  ) {}

  record BillPayment(String id, String paymentDate, BigDecimal amount, String currencyCode) {}

  private record BillPage(int page, int totalPages, List<CardBill> results) {}

  private record CreditAccounts(List<CreditAccount> results) {}

  private record AccountType(String type) {}

  private record AccountTypes(List<AccountType> results) {}

  private record Credentials(String clientId, String clientSecret) {}

  private record AuthenticationResponse(String apiKey) {}

  private record AccountResponse(List<BankAccount> results) {
    private AccountResponse {
      results = List.copyOf(results);
    }
  }

  public record BankAccount(
      UUID id, UUID itemId, String name, String subtype, String currencyCode, BigDecimal balance
  ) {}

  public record Transaction(
      String id, UUID accountId, String description, Instant date, String currencyCode,
      BigDecimal amount, BigDecimal amountInAccountCurrency, String type, String status,
      String operationType, CardTransactionMetadata creditCardMetadata
  ) {
    public Transaction(
        String id, UUID accountId, String description, Instant date, String currencyCode,
        BigDecimal amount, BigDecimal amountInAccountCurrency, String type, String status
    ) {
      this(id, accountId, description, date, currencyCode, amount, amountInAccountCurrency, type, status,
          null, null);
    }
  }

  public record CardTransactionMetadata(
      String billId, Integer installmentNumber, Integer totalInstallments, String billForecastDate,
      String paymentType
  ) {}

  public record TransactionPage(List<Transaction> results, String next) {
    public TransactionPage {
      results = List.copyOf(results);
    }
  }
}
