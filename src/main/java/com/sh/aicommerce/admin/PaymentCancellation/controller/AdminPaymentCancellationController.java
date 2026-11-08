package com.sh.aicommerce.admin.PaymentCancellation.controller;

import com.sh.aicommerce.admin.PaymentCancellation.dto.response.AdminPaymentCancellationListResponseDto;
import com.sh.aicommerce.admin.PaymentCancellation.service.AdminPaymentCancellationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
@RequestMapping("/api/admin/payment/cancel")
public class AdminPaymentCancellationController {
    private final AdminPaymentCancellationService service;

    @GetMapping("")
    public String page() {
        return "admin/payment/paymentCancellationList";
    }

    @GetMapping("/list")
    @ResponseBody
    public ResponseEntity<Page<AdminPaymentCancellationListResponseDto>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(service.cancellationList(page, size));
    }
}
