package com.sh.aicommerce.toss.service;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sh.aicommerce.common.exception.payment.PaymentException;
import com.sh.aicommerce.common.exception.toss.TossCancelException;
import com.sh.aicommerce.common.exception.toss.TossPaymentException;
import com.sh.aicommerce.common.exception.toss.TossRestClientException;
import com.sh.aicommerce.toss.dto.request.TossBillingPaymentRequestDto;
import com.sh.aicommerce.toss.dto.request.TossCancelRequestDto;
import com.sh.aicommerce.toss.dto.request.TossPaymentBillingRequestDto;
import com.sh.aicommerce.toss.dto.request.TossPaymentRequestDto;
import com.sh.aicommerce.toss.dto.response.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class TossPaymentClient {

    // 명확한 요청 거절로 분류한 코드만 FAILED로 처리한다.
    private static final Set<String> DEFINITE_CANCEL_REJECTION_CODES = Set.of(
            "INVALID_REQUEST",
            "INVALID_REFUND_ACCOUNT_INFO",
            "INVALID_REFUND_ACCOUNT_NUMBER",
            "INVALID_BANK"
    );

    private static final Set<String> PAYMENT_REJECTION_CODES = Set.of(
            "INVALID_STOPPED_CARD",               // 정지 카드
            "EXCEED_MAX_PAYMENT_AMOUNT",           // 일일 결제 금액 초과
            "EXCEED_MAX_AMOUNT",                   // 거래 금액 한도 초과
            "EXCEED_MAX_MONTHLY_PAYMENT_AMOUNT",   // 월 결제 한도 100만원 초과
            "REJECT_ACCOUNT_PAYMENT",             // 잔액 부족
            "REJECT_CARD_PAYMENT",                // 한도 초과 또는 잔액 부족
            "REJECT_CARD_COMPANY",                // 승인 거절
            "FORBIDDEN_REQUEST",                  // 요청 불허
            "EXCEED_MAX_ONE_DAY_AMOUNT",           // 일일 한도 초과
            "INVALID_PASSWORD"                    // 결제 비밀번호 불일치
    );

    @Value("${toss.payment.widget.secretKey}")
    private String tossSecretKey;

    @Value("${toss.payment.billing.secretKey}")
    private String tossBillingSecretKey;

    @Qualifier("tossRestTemplate")
    private final RestTemplate tossRestTemplate;

    private final ObjectMapper objectMapper;

    public TossPaymentSuccessResponseDto confirm(TossPaymentRequestDto request) throws JsonProcessingException {
        try {
            log.info("[토스 페이먼츠 API 요청] 요청 주문번호 :{}", request.getOrderId());

            // Toss Payment 결제 승인 요청
            String encodedSecretKey = Base64.getEncoder()
                    .encodeToString((tossSecretKey +
                            ":").getBytes(StandardCharsets.UTF_8));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Basic " + encodedSecretKey);

            HttpEntity<TossPaymentRequestDto> httpEntity = new
                    HttpEntity<>(request, headers);


            // Toss Payments API 응답 요청
            ResponseEntity<TossPaymentSuccessResponseDto> response =
                    tossRestTemplate.exchange(
                            "https://api.tosspayments.com/v1/payments/confirm",
                            HttpMethod.POST,
                            httpEntity,
                            TossPaymentSuccessResponseDto.class
                    );

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new PaymentException("토스 결제 승인에 실패했습니다.");
            }

            log.info("[토스 페이먼츠 결제 승인 완료] 응답 데이터 :{}", response.getBody());

            // 결제 승인이 성공적으로 완료되었다면 , DB에 저장된 Payment, Order에 대한 값 업데이트
            return response.getBody();
        } catch (HttpStatusCodeException e) {
            throw convertException(e);
        } catch (RestClientException e) {
            log.warn("[결제 승인 결과 확인 필요] 통슨 또는 응답 변환 오류 확인 필요", e);

            throw new TossRestClientException("[결제 승인 결과 확인필요] 통신 또는 응답 변환 오류 발생", e);
        }
    }

    public TossPaymentBillingResponseDto getBillingKey(TossPaymentBillingRequestDto request) {
        log.info("[토스 페이먼츠 카드 BillingKey 발급 요청] 요청키 :{}", request.getAuthKey());

        // Toss Payment 결제 승인 요청
        String encodedSecretKey = Base64.getEncoder()
                .encodeToString((tossBillingSecretKey +
                        ":").getBytes(StandardCharsets.UTF_8));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Basic " + encodedSecretKey);

        HttpEntity<TossPaymentBillingRequestDto> httpEntity = new
                HttpEntity<>(request, headers);


        // Toss Payments API 응답 요청
        ResponseEntity<TossPaymentBillingResponseDto> response =
                tossRestTemplate.exchange(
                        "https://api.tosspayments.com/v1/billing/authorizations/issue",
                        HttpMethod.POST,
                        httpEntity,
                        TossPaymentBillingResponseDto.class
                );

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new PaymentException("토스 페이먼츠 자동 결제 BillingKey 발급에 실패하였습니다.");
        }

        log.info("[토스 페이먼츠 자동결제 BillingKey 발급 완료] 응답 데이터 :{}", response.getBody().toString());

        // 결제 승인이 성공적으로 완료되었다면 , DB에 저장된 Payment, Order에 대한 값 업데이트
        return response.getBody();
    }

    public TossPaymentSuccessResponseDto cardBilling(String billingKey, String customerKey, Integer amount, String orderNumber, String orderName, String customerEmail, String customerName, Integer taxFreeAmount) throws JsonProcessingException {
        try {
            log.info("[토스페이먼츠 자동결제 승인 요청] 주문번호 : {}", orderNumber);

            String encodedSecretKey = Base64.getEncoder()
                    .encodeToString((tossBillingSecretKey +
                            ":").getBytes(StandardCharsets.UTF_8));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Basic " + encodedSecretKey);

            TossBillingPaymentRequestDto request = new TossBillingPaymentRequestDto(
                    customerKey,
                    amount,
                    orderNumber,
                    orderName,
                    customerEmail,
                    customerName,
                    taxFreeAmount
            );

            HttpEntity<TossBillingPaymentRequestDto> httpEntity =
                    new HttpEntity<>(request, headers);

            ResponseEntity<TossPaymentSuccessResponseDto> response =
                    tossRestTemplate.exchange(
                            "https://api.tosspayments.com/v1/billing/" + billingKey,
                            HttpMethod.POST,
                            httpEntity,
                            TossPaymentSuccessResponseDto.class
                    );

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                //토스페이먼츠 자동 결제가 실패한 경우에는 예약 내역 해제까지
                throw new PaymentException("토스페이먼츠 자동결제 승인에 실패했습니다.");
            }

            log.info("[토스페이먼츠 자동결제 승인 완료] 주문번호 : {}", orderNumber);

            return response.getBody();

        } catch (HttpStatusCodeException e) {
            throw convertException(e);
        } catch (RestClientException e) {
            log.warn("[결제 승인 결과 확인 필요] 통슨 또는 응답 변환 오류 확인 필요", e);
            throw new TossRestClientException("[결제 승인 결과 확인필요] 통신 또는 응답 변환 오류 발생", e);
        }
    }

    public TossPaymentCancelResponseDto cancel(String paymentKey){
        try {
            String encodedSecretKey = Base64.getEncoder()
                    .encodeToString((tossBillingSecretKey +
                            ":").getBytes(StandardCharsets.UTF_8));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Basic " + encodedSecretKey);

            TossCancelRequestDto request = new TossCancelRequestDto("고객 요청에 의한 환불 처리");

            HttpEntity<TossCancelRequestDto> httpEntity =
                    new HttpEntity<>(request, headers);

            ResponseEntity<TossPaymentCancelResponseDto> response =
                    tossRestTemplate.exchange(
                            "https://api.tosspayments.com/v1/payments/" + paymentKey + "/cancel",
                            HttpMethod.POST,
                            httpEntity,
                            TossPaymentCancelResponseDto.class
                    );

            if(response.getBody() == null)
                throw new TossRestClientException("[PG 결제 취소 결과 확인 필요] 응답 본문이 비어 있습니다.");

            return response.getBody();
        } catch (HttpStatusCodeException e) {
            TossPaymentErrorResponseDto error;

            try{
                error = objectMapper.readValue(e.getResponseBodyAsString(), TossPaymentErrorResponseDto.class);

            }catch (JsonProcessingException parseException) {
                throw new TossRestClientException(
                        "[PG 결제 취소 결과 확인 필요] 오류 응답을 해석하지 못했습니다. | HTTP 상태: "
                                + e.getStatusCode().value(),
                        e
                );
            }
            if (error == null || error.getCode() == null) {
                throw new TossRestClientException(
                        "[PG 결제 취소 결과 확인 필요] 오류 응답에 코드가 없습니다.",
                        e
                );
            }
            log.warn(
                    "[토스 결제 취소 오류 응답] status: {}, code: {}, message : {}",
                    e.getStatusCode().value(),
                    error.getCode(),
                    error.getMessage()
            );

            // 서버 오류는 코드가 있어도 실제 취소 결과를 확정할 수 없다.
            if (e.getStatusCode().is5xxServerError()) {
                throw new TossRestClientException(
                        "[PG 결제 취소 결과 확인 필요] 서버 오류로 결과를 확정할 수 없습니다. | PG 오류 코드: " + error.getCode(), e);
            }

            if ("ALREADY_CANCELED_PAYMENT".equals(error.getCode())) {
                return findPaymentForCancellation(paymentKey);
            }

            if (e.getStatusCode().is4xxClientError()
                    && DEFINITE_CANCEL_REJECTION_CODES.contains(error.getCode())) {
                throw new TossCancelException(
                        error.getCode(),     // Refund.failureCode에 저장할 PG 오류 코드
                        "[PG 결제 취소 거절] " + error.getMessage() + " | PG 오류 코드: " + error.getCode(),
                        e                    // 원인 예외 보존
                );
            }

            // 아직 분류하지 않은 코드는 실패로 단정하지 않고 조회·복구 대상으로 보낸다.
            throw new TossRestClientException(
                    "[PG 결제 취소 결과 확인 필요] 미분류 오류로 결과를 확정할 수 없습니다. | PG 오류 코드: " + error.getCode(), e);
        } catch (RestClientException e) {
            // 타임아웃·통신 오류·성공 응답 변환 오류 등
            throw new TossRestClientException(
                    "[PG 결제 취소 결과 확인 필요] 통신 또는 응답 변환 오류로 결과를 확인하지 못했습니다.",
                    e
            );
        }
    }

    private TossPaymentCancelResponseDto findPaymentForCancellation(
            String paymentKey
    ) {
        String encodedSecretKey = Base64.getEncoder()
                .encodeToString(
                        (tossBillingSecretKey + ":")
                                .getBytes(StandardCharsets.UTF_8)
                );

        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(encodedSecretKey);

        HttpEntity<Void> httpEntity = new HttpEntity<>(headers);

        try {
            ResponseEntity<TossPaymentCancelResponseDto> response =
                    tossRestTemplate.exchange(

                            "https://api.tosspayments.com/v1/payments/{paymentKey}",
                            HttpMethod.GET,
                            httpEntity,
                            TossPaymentCancelResponseDto.class,
                            paymentKey
                    );

            if (!response.getStatusCode().is2xxSuccessful()
                    || response.getBody() == null) {
                throw new TossRestClientException(
                        "[PG 결제 결과 조회] 이미 취소된 결제의 유효한 조회 응답이 없습니다."
                );
            }

            return response.getBody();

        } catch (RestClientException e) {
            throw new TossRestClientException(
                    "[PG 결제 결과 조회] 통신 또는 응답 변환 오류로 조회 결과를 확인하지 못했습니다.",
                    e
            );
        }
    }

    public TossPaymentRequestHistoryResponseDto getPaymentRequestHistory(String paymentKey) {
        String encodedSecretKey = Base64.getEncoder()
                .encodeToString(
                        (tossBillingSecretKey + ":")
                                .getBytes(StandardCharsets.UTF_8)
                );

        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(encodedSecretKey);

        HttpEntity<Void> httpEntity = new HttpEntity<>(headers);

        try {
            ResponseEntity<TossPaymentRequestHistoryResponseDto> response =
                    tossRestTemplate.exchange(

                            "https://api.tosspayments.com/v1/payments/{paymentKey}",
                            HttpMethod.GET,
                            httpEntity,
                            TossPaymentRequestHistoryResponseDto.class,
                            paymentKey
                    );

            if (!response.getStatusCode().is2xxSuccessful()
                    || response.getBody() == null) {
                throw new TossRestClientException(
                        "[PG 결제 결과 조회]  결제의 유효한 조회 응답이 없습니다."
                );
            }

            return response.getBody();

        } catch (RestClientException e) {
            throw new TossRestClientException(
                    "[PG 결제 결과 조회] 통신 또는 응답 변환 오류로 조회 결과를 확인하지 못했습니다.",
                    e
            );
        }
    }

    private RuntimeException convertException(HttpStatusCodeException codeException) {

        if (codeException.getStatusCode().is5xxServerError()) {
            return new TossRestClientException(
                    "[결제 승인 결과 확인 필요] PG 서버 오류",codeException);
        }

        TossPaymentErrorResponseDto errorResponse ;

        try {
            errorResponse = objectMapper
                    .readValue(codeException.getResponseBodyAsString(), TossPaymentErrorResponseDto.class);
        } catch (JsonProcessingException e) {
            codeException.addSuppressed(e);

            return new TossRestClientException(
                    "[결제 승인 결과 확인 필요] 오류 응답 해석 실패",
                    codeException
            );
        }

        if (errorResponse == null || errorResponse.getCode() == null) {
            throw new RestClientException("[결제 승인 요청 결과 확인필요] 오류 코드 누락");
        }

        if (codeException.getStatusCode().is4xxClientError()
                && PAYMENT_REJECTION_CODES.contains(errorResponse.getCode())) {
            throw new TossPaymentException("결제 요청이 실패 했습니다. 결제 실패 에러 코드 : " + errorResponse.getCode() + " 메세지 : " + errorResponse.getMessage());
        }

        return new TossRestClientException("[결제 승인 결과 확인 필요] 에러 코드 : " + errorResponse.getCode() + " 에러 메세지 : " + errorResponse.getMessage());
    }
}
