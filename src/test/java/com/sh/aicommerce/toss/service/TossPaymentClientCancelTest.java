package com.sh.aicommerce.toss.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sh.aicommerce.common.exception.toss.TossCancelException;
import com.sh.aicommerce.common.exception.toss.TossRestClientException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class TossPaymentClientCancelTest {
    private TossPaymentClient client;
    private MockRestServiceServer server;

    @BeforeEach
    void setup() {
        RestTemplate template = new RestTemplate();
        server = MockRestServiceServer.createServer(template);
        client = new TossPaymentClient(template, new ObjectMapper(), null);
    }

    private void error(HttpStatus status, String code) {
        server.expect(requestTo("https://api.tosspayments.com/v1/payments/test-key/cancel"))
                .andRespond(withStatus(status).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"" + code + "\",\"message\":\"test\"}"));
    }

    @Test
    void knownRejectionIsFailed() {
        error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST");
        assertThrows(TossCancelException.class, () -> client.cancel("test-key"));
        server.verify();
    }

    @Test
    void serverErrorRequiresCheckEvenWithKnownCode() {
        error(HttpStatus.INTERNAL_SERVER_ERROR, "INVALID_REQUEST");
        assertThrows(TossRestClientException.class, () -> client.cancel("test-key"));
        server.verify();
    }

    @Test
    void unknownCodeRequiresCheck() {
        error(HttpStatus.BAD_REQUEST, "NEW_ERROR");
        assertThrows(TossRestClientException.class, () -> client.cancel("test-key"));
        server.verify();
    }

    @Test
    void alreadyCanceledFetchesPayment() {
        error(HttpStatus.BAD_REQUEST, "ALREADY_CANCELED_PAYMENT");
        server.expect(requestTo("https://api.tosspayments.com/v1/payments/test-key"))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"status\":\"CANCELED\"}"));
        assertEquals("CANCELED", client.cancel("test-key").getStatus());
        server.verify();
    }
}
