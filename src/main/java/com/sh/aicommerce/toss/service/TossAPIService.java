package com.sh.aicommerce.toss.service;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sh.aicommerce.common.exception.orderCancellation.OrderCancellationException;
import com.sh.aicommerce.common.exception.payment.PaymentException;
import com.sh.aicommerce.common.exception.refund.RefundException;
import com.sh.aicommerce.common.exception.toss.TossCancelException;
import com.sh.aicommerce.common.exception.toss.TossRestClientException;
import com.sh.aicommerce.entity.OrderCancellation;
import com.sh.aicommerce.entity.Orders;
import com.sh.aicommerce.entity.Payment;
import com.sh.aicommerce.entity.TossPaymentLog;
import com.sh.aicommerce.enums.order.OrderCancelStatus;
import com.sh.aicommerce.enums.payment.CardCompany;
import com.sh.aicommerce.enums.payment.PaymentStatus;
import com.sh.aicommerce.order.service.OrderService;
import com.sh.aicommerce.orderCancellation.service.OrderCancellationService;
import com.sh.aicommerce.payment.repository.PaymentRepository;
import com.sh.aicommerce.payment.service.PaymentTransactionService;
import com.sh.aicommerce.toss.dto.request.TossPaymentBillingRequestDto;
import com.sh.aicommerce.toss.dto.request.TossPaymentRequestDto;
import com.sh.aicommerce.toss.dto.response.*;
import com.sh.aicommerce.toss.repository.TossPaymentLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import javax.management.RuntimeMBeanException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Service
@RequiredArgsConstructor
@Slf4j
public class TossAPIService {


    private final PaymentTransactionService paymentTransactionService;
    private final TossTransactionService transactionService;
    private final TossPaymentClient client;
    private final OrderCancellationService cancellationService;

    // 카드 등록시 billingKey에 대한 값 획득
    public void getBillingKey(String customerKey, String authKey) {

        transactionService.validateMemberByCustomerKey(customerKey);
        TossPaymentBillingResponseDto response = client.getBillingKey(new TossPaymentBillingRequestDto(customerKey, authKey));
        log.info("[토스페이먼츠 API 자동결제(Billing)] 카드 BillingKey 정상적으로 발급 완료");
        // 이미 등로된 BillingKey에 대한 값이 존재하면 중복 카드 등록
        transactionService.validateCard(response);

        //정상적으로 BillingKey 값이 발급이 되었다면 DB에 저장 Card에 대한 값  저장
        transactionService.saveCard(customerKey,response);
        log.info("[토스페이먼츠 API 자동결제(Billing)] 카드 Billing Key 발급 및 카드 DB 저장 완료");
    }

    // 결제 위젯에서 결제 실패 처리
    public void payFail(String orderNumber, String errorCode, String errorMessage) {
        log.error("[토스페이먼츠 결제 위젯 오류 발생] 에러 코드 : {}, 에러 메세지 : {}", errorCode, errorMessage);

        paymentTransactionService.payFailTossPayment(orderNumber);
    }

    public void cancel(Long cancellationId) {
        log.info("[토스페이먼츠 결제 취소 요청]");
        TossPaymentCancelResponseDto response;
        Payment payment = paymentTransactionService.prepareRefund(cancellationId);

        try {
            // TossAPI 결제 취소 요청
            response = client.cancel(payment.getPaymentKey());
            // 결제 실패 요청이 성공적으로 완료 처리
            paymentTransactionService.cancel(response, cancellationId);
        } catch (TossCancelException e) {
            log.error("[토스페이먼츠 결제 취소 요청 처리 실패] 에러 메세지 : {}", e.getMessage());

            try {
                // 카드 정지나 TossAPI에서 설정된 에러 코드를 보내주는 경우에 Failed로 작성
                cancellationService.paymentCancelFailed(
                        cancellationId,
                        e.getErrorCode(),
                        e.getMessage());

            } catch (RuntimeException saveException) {
                e.addSuppressed(saveException);
            }
            throw e;

        } catch (TossRestClientException e) {
            log.error("[토스페이먼츠 결제 취소 요청 통신 에러 발생] 메세지 : {}", e.getMessage());

            try {
                // 타임아웃 외부 API의 연결에 실패한 경우 해당 내역을 확인할 수 있도록 상태 업데이트
                cancellationService.paymentCancelFailedCheck(e.getMessage(), cancellationId);
            } catch (RuntimeException saveException) {
                e.addSuppressed(saveException);

                log.error(
                        "[결제 취소 결과 확인 필요] cancellationId: {}",
                        cancellationId,
                        e
                );
            }

            throw e;
        } catch (RuntimeException e) {
            try {
                cancellationService.paymentCancelFailedCheck(
                        // 검증 원인은 보존하되 DB 오류 전문 등은 관리자 사유에 노출하지 않는다.
                        (e instanceof PaymentException || e instanceof RefundException
                                || e instanceof OrderCancellationException)
                                ? e.getMessage()
                                : "[결제 취소 내부 처리] 응답 처리 또는 DB 반영 중 오류가 발생했습니다. PG 결과 확인이 필요합니다."
                                        + " | 취소 ID: " + cancellationId,
                        cancellationId
                );
            } catch (RuntimeException saveException) {
                e.addSuppressed(saveException);
            }

            throw e;
        }
    }
}