package com.noctivagous.ledgerpay.controller;

import com.jayway.jsonpath.JsonPath;
import com.noctivagous.ledgerpay.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class TransferControllerTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void transfer_movesMoneyAndWritesLedgerEntries() throws Exception {
        long from = createAccount();
        long to = createAccount();
        deposit(from, "100.00").andExpect(status().isCreated());

        transfer(from, to, "40.00", UUID.randomUUID().toString())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transferId").exists());

        mockMvc.perform(get("/api/accounts/{id}", from).with(auth()))
                .andExpect(jsonPath("$.balance").value(60.0));
        mockMvc.perform(get("/api/accounts/{id}", to).with(auth()))
                .andExpect(jsonPath("$.balance").value(40.0));
        mockMvc.perform(get("/api/accounts/{id}/entries", from).with(auth()))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].amount").value(-40.0));
    }

    @Test
    void replayedRequest_returnsSameTransferAndMovesMoneyOnce() throws Exception {
        long from = createAccount();
        long to = createAccount();
        deposit(from, "100.00").andExpect(status().isCreated());
        String key = UUID.randomUUID().toString();

        String first = transfer(from, to, "30.00", key).andReturn().getResponse().getContentAsString();
        String replay = transfer(from, to, "30.00", key).andReturn().getResponse().getContentAsString();

        assertEquals(first, replay);
        mockMvc.perform(get("/api/accounts/{id}", from).with(auth()))
                .andExpect(jsonPath("$.balance").value(70.0));
    }

    @Test
    void insufficientFunds_returns422ProblemDetail() throws Exception {
        long from = createAccount();
        long to = createAccount();

        transfer(from, to, "1.00", UUID.randomUUID().toString())
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Insufficient balance in account " + from));
    }

    @Test
    void missingIdempotencyKey_returns400() throws Exception {
        mockMvc.perform(post("/api/transfers").with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromAccountId": 1, "toAccountId": 2, "amount": 10}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requestWithoutCredentials_returns401() throws Exception {
        mockMvc.perform(get("/api/accounts/{id}", 1))
                .andExpect(status().isUnauthorized());
    }

    private long createAccount() throws Exception {
        String json = mockMvc.perform(post("/api/accounts").with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ownerId": 7}"""))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.<Number>read(json, "$.id").longValue();
    }

    private ResultActions deposit(long accountId, String amount) throws Exception {
        return mockMvc.perform(post("/api/accounts/{id}/deposits", accountId).with(auth())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"amount": %s}""".formatted(amount)));
    }

    private ResultActions transfer(long from, long to, String amount, String idempotencyKey) throws Exception {
        return mockMvc.perform(post("/api/transfers").with(auth())
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fromAccountId": %d, "toAccountId": %d, "amount": %s}""".formatted(from, to, amount)));
    }

    private static RequestPostProcessor auth() {
        return httpBasic("ledgerpay", "ledgerpay");
    }
}
