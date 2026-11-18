package com.pedro.ledger.infrastructure.web.creditcard;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pedro.ledger.application.creditcard.CreditCardApplicationService;
import com.pedro.ledger.application.creditcard.CreditCardInvoiceApplicationService;
import com.pedro.ledger.application.creditcard.CreditCardPurchaseApplicationService;
import com.pedro.ledger.domain.creditcard.CreditCard;
import com.pedro.ledger.domain.creditcard.CreditCardInvoice;
import com.pedro.ledger.domain.creditcard.CreditCardInvoicePayment;
import com.pedro.ledger.domain.creditcard.CreditCardPurchase;
import com.pedro.ledger.domain.money.Money;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({
    CreditCardController.class,
    CreditCardPurchaseController.class,
    CreditCardInvoiceController.class
})
class CreditCardControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private CreditCardApplicationService cardService;

  @MockitoBean
  private CreditCardPurchaseApplicationService purchaseService;

  @MockitoBean
  private CreditCardInvoiceApplicationService invoiceService;

  @Test
  void shouldCreateCard() throws Exception {
    CreditCard card = CreditCard.open(
        "Platinum",
        "Bank",
        Money.of("5000.00"),
        10,
        15
    );

    when(cardService.create(
        "Platinum",
        "Bank",
        new BigDecimal("5000.00"),
        10,
        15,
        "BRL"
    )).thenReturn(card);
    when(cardService.committedAmount(card)).thenReturn(Money.zero());

    mockMvc.perform(
            post("/credit-cards")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Platinum",
                      "issuer": "Bank",
                      "creditLimit": 5000.00,
                      "closingDay": 10,
                      "dueDay": 15,
                      "currency": "BRL"
                    }
                    """)
        )
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(card.getId().toString()))
        .andExpect(jsonPath("$.name").value("Platinum"))
        .andExpect(jsonPath("$.issuer").value("Bank"))
        .andExpect(jsonPath("$.creditLimit").value(5000.00))
        .andExpect(jsonPath("$.availableLimit").value(5000.00))
        .andExpect(jsonPath("$.closingDay").value(10))
        .andExpect(jsonPath("$.dueDay").value(15))
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(jsonPath("$.currency").value("BRL"));
  }

  @Test
  void shouldRejectInvalidCardRequest() throws Exception {
    mockMvc.perform(
            post("/credit-cards")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Platinum",
                      "issuer": "Bank",
                      "creditLimit": 5000.00,
                      "closingDay": 32,
                      "dueDay": 15
                    }
                    """)
        )
        .andExpect(status().isBadRequest());

    verifyNoInteractions(cardService);
  }

  @Test
  void shouldFindCardById() throws Exception {
    UUID id = UUID.randomUUID();
    CreditCard card = CreditCard.restore(
        id,
        "Platinum",
        "Bank",
        Money.of("5000.00"),
        10,
        15,
        com.pedro.ledger.domain.creditcard.CreditCardStatus.ACTIVE
    );

    when(cardService.findById(id)).thenReturn(Optional.of(card));
    when(cardService.committedAmount(card)).thenReturn(Money.of("100.00"));

    mockMvc.perform(get("/credit-cards/{id}", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id.toString()))
        .andExpect(jsonPath("$.availableLimit").value(4900.00));

    verify(cardService).findById(id);
  }

  @Test
  void shouldReturnNotFoundForUnknownCard() throws Exception {
    UUID id = UUID.randomUUID();

    when(cardService.findById(id)).thenReturn(Optional.empty());

    mockMvc.perform(get("/credit-cards/{id}", id))
        .andExpect(status().isNotFound());
  }

  @Test
  void shouldCreatePurchase() throws Exception {
    UUID cardId = UUID.randomUUID();
    UUID invoiceId = UUID.randomUUID();
    UUID purchaseId = UUID.randomUUID();
    CreditCardPurchase purchase = CreditCardPurchase.create(
        purchaseId,
        cardId,
        "Notebook",
        Money.of("100.00"),
        LocalDate.of(2026, 5, 10),
        null,
        2,
        List.of(invoiceId, UUID.randomUUID()),
        Instant.parse("2026-05-15T12:00:00Z")
    );

    when(purchaseService.create(
        eq(cardId),
        eq(purchaseId),
        eq(new BigDecimal("100.00")),
        eq("Notebook"),
        eq(LocalDate.of(2026, 5, 10)),
        eq(null),
        eq(2)
    )).thenReturn(purchase);

    mockMvc.perform(
            post("/credit-cards/{cardId}/purchases", cardId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "id": "%s",
                      "description": "Notebook",
                      "amount": 100.00,
                      "purchaseDate": "2026-05-10",
                      "installmentCount": 2
                    }
                    """.formatted(purchaseId))
        )
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(purchaseId.toString()))
        .andExpect(jsonPath("$.installmentCount").value(2))
        .andExpect(jsonPath("$.installments.length()").value(2))
        .andExpect(jsonPath("$.installments[0].amount").value(50.00));
  }

  @Test
  void shouldFindInvoiceDetails() throws Exception {
    UUID cardId = UUID.randomUUID();
    UUID invoiceId = UUID.randomUUID();
    CreditCardInvoice invoice = CreditCardInvoice.open(
        cardId,
        LocalDate.of(2026, 5, 1),
        LocalDate.of(2026, 5, 10),
        LocalDate.of(2026, 5, 10),
        LocalDate.of(2026, 6, 15),
        Money.of("0.00").currency(),
        LocalDate.of(2026, 5, 15)
    );
    invoice.addCharge(Money.of("100.00"), LocalDate.of(2026, 5, 15));

    when(invoiceService.findById(cardId, invoiceId))
        .thenReturn(invoice);
    when(invoiceService.findPurchases(cardId, invoiceId))
        .thenReturn(List.of());

    mockMvc.perform(
            get(
                "/credit-cards/{cardId}/invoices/{invoiceId}",
                cardId,
                invoiceId
            )
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.invoice.totalAmount").value(100.00))
        .andExpect(jsonPath("$.invoice.remainingAmount").value(100.00))
        .andExpect(jsonPath("$.invoice.status").value("CLOSED"))
        .andExpect(jsonPath("$.purchases.length()").value(0));
  }

  @Test
  void shouldPayInvoice() throws Exception {
    UUID cardId = UUID.randomUUID();
    UUID invoiceId = UUID.randomUUID();
    UUID accountId = UUID.randomUUID();
    CreditCardInvoice invoice = CreditCardInvoice.open(
        cardId,
        LocalDate.of(2026, 5, 1),
        LocalDate.of(2026, 5, 10),
        LocalDate.of(2026, 5, 10),
        LocalDate.of(2026, 6, 15),
        Money.of("0.00").currency(),
        LocalDate.of(2026, 5, 15)
    );
    invoice.addCharge(Money.of("100.00"), LocalDate.of(2026, 5, 15));
    invoice.registerPayment(Money.of("100.00"));

    CreditCardInvoicePayment payment = CreditCardInvoicePayment.create(
        UUID.randomUUID(),
        invoiceId,
        accountId,
        Money.of("100.00"),
        Instant.parse("2026-05-15T12:00:00Z")
    );

    when(invoiceService.pay(
        eq(cardId),
        eq(invoiceId),
        eq(null),
        eq(accountId),
        eq(new BigDecimal("100.00"))
    )).thenReturn(
        new CreditCardInvoiceApplicationService.PaymentResult(
            payment,
            invoice
        )
    );

    mockMvc.perform(
            post(
                "/credit-cards/{cardId}/invoices/{invoiceId}/payments",
                cardId,
                invoiceId
            )
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "accountId": "%s",
                      "amount": 100.00
                    }
                    """.formatted(accountId))
        )
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.payment.amount").value(100.00))
        .andExpect(jsonPath("$.invoice.status").value("PAID"))
        .andExpect(jsonPath("$.invoice.remainingAmount").value(0.00));
  }

  @Test
  void shouldRejectPaymentAboveRemaining() throws Exception {
    UUID cardId = UUID.randomUUID();
    UUID invoiceId = UUID.randomUUID();
    UUID accountId = UUID.randomUUID();

    when(invoiceService.pay(
        any(UUID.class),
        any(UUID.class),
        any(),
        any(UUID.class),
        any(BigDecimal.class)
    )).thenThrow(new IllegalArgumentException(
        "Payment cannot exceed the remaining invoice amount"
    ));

    mockMvc.perform(
            post(
                "/credit-cards/{cardId}/invoices/{invoiceId}/payments",
                cardId,
                invoiceId
            )
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "accountId": "%s",
                      "amount": 100.01
                    }
                    """.formatted(accountId))
        )
        .andExpect(status().isBadRequest());
  }
}
