package com.pedro.ledger.infrastructure.openfinance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.pedro.ledger.domain.account.AccountSource;
import com.pedro.ledger.domain.account.AccountStatus;
import com.pedro.ledger.domain.account.AccountType;
import com.pedro.ledger.infrastructure.web.account.AccountResponse;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PluggyPreviewTest {

  private static final UUID ITEM_ID = UUID.fromString("b15058f6-21aa-4b3f-ab37-eea355c3e158");
  private static final UUID OTHER_ITEM = UUID.fromString("b15058f6-21aa-4b3f-ab37-eea355c3e159");
  private static final UUID ACCOUNT_ID = UUID.fromString("0d2f43f3-222f-4e88-b9ea-6c59895862c6");
  private static final LocalDate FROM = LocalDate.parse("2026-11-01");
  private static final LocalDate TO = LocalDate.parse("2026-11-21");

  private final PluggyClient client = mock(PluggyClient.class);
  private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
  private final PrintStream out = new PrintStream(bytes, true, StandardCharsets.UTF_8);

  @Test
  void listsAccountsFromBothConfiguredItems() {
    when(client.listBankAccounts(ITEM_ID)).thenReturn(List.of(account()));
    when(client.listBankAccounts(OTHER_ITEM)).thenReturn(List.of());

    PluggyPreview.parse(new String[]{"accounts", ITEM_ID.toString(), OTHER_ITEM.toString()})
        .run(client, out);

    assertThat(output()).contains(ITEM_ID.toString(), OTHER_ITEM.toString(),
        ACCOUNT_ID.toString(), "Conta corrente", "BRL 100.01", "0 contas bancárias");
  }

  @Test
  void previewsAtMostTwentyTransactionsAndRemovesTerminalControlCharacters() {
    when(client.listBankAccounts(ITEM_ID)).thenReturn(List.of(account()));
    var transaction = new PluggyClient.Transaction("external", ACCOUNT_ID, "Compra\u001b\nloja",
        Instant.parse("2026-11-10T12:00:00Z"), "BRL", new BigDecimal("-10.25"), null,
        "DEBIT", "POSTED");
    when(client.listTransactions(ACCOUNT_ID, FROM, TO))
        .thenReturn(new PluggyClient.TransactionPage(Collections.nCopies(21, transaction), "?after=x"));

    transactionCommand().run(client, out);

    assertThat(output()).contains("DEBIT | BRL -10.25 | POSTED | Compra  loja", "até 20 registros")
        .doesNotContain("\u001b");
    assertThat(output().lines().filter(line -> line.contains("POSTED")).count()).isEqualTo(20);
  }

  @Test
  void refusesTransactionsForAccountsOutsideTheSelectedItem() {
    when(client.listBankAccounts(ITEM_ID)).thenReturn(List.of());

    assertThatThrownBy(() -> transactionCommand().run(client, out))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("não pertence");
    org.mockito.Mockito.verify(client).listBankAccounts(ITEM_ID);
    verifyNoMoreInteractions(client);
  }

  @Test
  void refusesAnInvalidPeriod() {
    assertThatThrownBy(() -> PluggyPreview.parse(new String[]{
        "transactions", ITEM_ID.toString(), ACCOUNT_ID.toString(), TO.toString(), FROM.toString()
    })).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("data final");
  }

  @Test
  void previewsTheExplicitPairAndInclusiveCutoff() {
    when(client.listBankAccounts(ITEM_ID)).thenReturn(List.of(account()));
    var local = new AccountResponse(OTHER_ITEM, "Conta no Finora", AccountType.CHECKING,
        AccountStatus.ACTIVE, BigDecimal.ZERO, AccountSource.MANUAL);

    PluggyPreview.printLinkPreview(client, List.of(local), OTHER_ITEM, ITEM_ID, ACCOUNT_ID, FROM, out);

    assertThat(output()).contains("Conta no Finora", "Conta corrente", "2026-11-01 (inclusive)",
        "Nenhum vínculo ou lançamento foi gravado");
  }

  @Test
  void refusesAPairWithoutAnActiveFinoraAccount() {
    assertThatThrownBy(() ->
        PluggyPreview.printLinkPreview(client, List.of(), OTHER_ITEM, ITEM_ID, ACCOUNT_ID, FROM, out))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Conta ativa não encontrada");
    verifyNoMoreInteractions(client);
  }

  private static PluggyPreview.Command transactionCommand() {
    return PluggyPreview.parse(new String[]{
        "transactions", ITEM_ID.toString(), ACCOUNT_ID.toString(), FROM.toString(), TO.toString()
    });
  }

  private static PluggyClient.BankAccount account() {
    return new PluggyClient.BankAccount(
        ACCOUNT_ID, ITEM_ID, "Conta corrente", "CHECKING_ACCOUNT", "BRL", new BigDecimal("100.01")
    );
  }

  private String output() {
    return bytes.toString(StandardCharsets.UTF_8);
  }
}
