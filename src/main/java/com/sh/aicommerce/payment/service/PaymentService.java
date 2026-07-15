package com.sh.aicommerce.payment.service;

import com.sh.aicommerce.payment.dto.request.PaymentRequestDto;
import com.sh.aicommerce.payment.dto.request.PreparePaymentResultDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {
    private final PaymentPrepareService paymentPrepareService;

    public PreparePaymentResultDto pay(Long memberId, Long variantId, Long optionId, PaymentRequestDto paymentRequestDto) {
        PreparePaymentResultDto preparePaymentResultDto = paymentPrepareService.preparePay(memberId, variantId, optionId, paymentRequestDto);

        log.info("[결제 준비 완료] 주문 번호 : {}, 결제번호 : {}", preparePaymentResultDto.getOrderId(), preparePaymentResultDto.getPaymentId());
        return preparePaymentResultDto;
    }
}