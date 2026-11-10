package com.sh.aicommerce.toss.service;


import com.sh.aicommerce.orderCancellation.service.OrderCancellationService;
import com.sh.aicommerce.payment.service.PaymentTransactionService;
import com.sh.aicommerce.toss.dto.payment.request.TossPaymentBillingRequestDto;
import com.sh.aicommerce.toss.dto.response.*;
import com.sh.aicommerce.toss.dto.transaction.TransactionHistoryResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

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
        log.info("[등록된 카드 정보]");
        log.info(response.toString());
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

        paymentTransactionService.payFailTossPayment(orderNumber, errorMessage);
    }
}