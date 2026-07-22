package com.sh.aicommerce.payment.controller;

import com.sh.aicommerce.payment.dto.request.PaymentRequestDto;
import com.sh.aicommerce.payment.service.PaymentPrepareService;
import com.sh.aicommerce.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/pay")
@Slf4j
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("{variantId}")
    public ResponseEntity<?> prepare(
            @RequestBody PaymentRequestDto paymentRequestDto,
            @PathVariable(name = "variantId") Long variantId,
            @RequestParam("optionId") Long optionId
//            Authentication authentication
    ) {
        Long memberId = 1L;
        log.info("결제 준비 요청 : {}", paymentRequestDto.toString());
        return ResponseEntity.ok(paymentService.preparePay(memberId, variantId, optionId, paymentRequestDto));
    }

    @PostMapping("cardBilling")
    public ResponseEntity<?> payByCardBilling(
//            Authentication authentication
            @RequestBody String orderNumber
    ) {
        Long memberId = 1L;
        return ResponseEntity.ok(paymentService.payByTossBillingCard(memberId, orderNumber));
    }
}