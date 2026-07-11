package com.sh.aicommerce.payment.controller;

import com.sh.aicommerce.payment.dto.request.PaymentRequestDto;
import com.sh.aicommerce.payment.service.PaymentPrepareService;
import com.sh.aicommerce.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/pay")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("{variantId}")
    public ResponseEntity<?> pay(
            @RequestBody PaymentRequestDto paymentRequestDto,
            @PathVariable(name = "variantId") Long variantId,
            @RequestParam("optionId") Long optionId
//            Authentication authentication
    ) {
        Long memberId = 1L;
        return ResponseEntity.ok(paymentService.pay(memberId, variantId, optionId, paymentRequestDto));
    }
}