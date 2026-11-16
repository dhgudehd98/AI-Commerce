package com.sh.aicommerce.toss.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sh.aicommerce.common.exception.toss.TossCancelException;
import com.sh.aicommerce.toss.dto.cancel.request.TossCancelRequestDto;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class PaymentCancellationClientRejectionTest {
    @Test
    void 명시적_취소_거절_HTTP응답을_오류코드와_함께_구분한다() {
        RestTemplate cancel = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(cancel);
        TossPaymentClient client = new TossPaymentClient(new RestTemplate(), cancel, new ObjectMapper());
        ReflectionTestUtils.setField(client, "tossBillingSecretKey", "test-unit-key");
        server.expect(requestTo("https://api.tosspayments.com/v1/payments/test-payment-key/cancel"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Idempotency-Key", "test-cancel-idempotency"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"INVALID_REQUEST\",\"message\":\"test rejection\"}"));
        TossCancelException error = assertThrows(TossCancelException.class, () -> client.cancel(
                new TossCancelRequestDto("test-payment-key", "test-order", "test-cancel-idempotency", "test reason")));
        assertEquals("INVALID_REQUEST", error.getErrorCode());
        server.verify();
    }
}
