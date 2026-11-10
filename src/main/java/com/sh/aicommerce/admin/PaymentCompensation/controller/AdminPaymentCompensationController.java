package com.sh.aicommerce.admin.PaymentCompensation.controller;

import com.sh.aicommerce.admin.PaymentCompensation.dto.response.AdminPaymentCompensationListResponseDto;
import com.sh.aicommerce.admin.PaymentCompensation.service.AdminPaymentCompensationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
@RequestMapping("/api/admin/payment/compensation")
public class AdminPaymentCompensationController {
    private final AdminPaymentCompensationService service;

    @GetMapping("")
    public String page() {
        return "admin/payment/paymentCompensationList";
    }

    @GetMapping("/list")
    @ResponseBody
    public ResponseEntity<Page<AdminPaymentCompensationListResponseDto>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(service.compensationList(page, size));
    }
}
