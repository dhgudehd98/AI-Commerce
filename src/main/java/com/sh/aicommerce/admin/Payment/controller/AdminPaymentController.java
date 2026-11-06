package com.sh.aicommerce.admin.Payment.controller;


import com.sh.aicommerce.admin.Payment.service.AdminPaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@RequestMapping("/api/admin/payment")
@Slf4j
@Controller
@RequiredArgsConstructor
public class AdminPaymentController {

    private final AdminPaymentService paymentService;

    @GetMapping("")
    public String getPayment() {
        return "payment/paymentList";
    }
    @GetMapping("list")
    @ResponseBody
    public ResponseEntity<?> paymentList(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(paymentService.paymentList(page, size));
    }
}