package com.sh.aicommerce.order.controller;


import com.sh.aicommerce.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/order")
public class OrderController {

    private final OrderService orderService;

    // 구매하기 버튼 클릭시 -> 주문서 생성
    @GetMapping("/sheet/{variantId}")
    public ResponseEntity<?> orderSheet(
            @PathVariable(name = "variantId") Long variantId,
            @RequestParam("optionId") Long optionId
//            Authentication authentication
    ) {
        // 일단 여기에서 회원 / 비회원 가입 여부 파악 ->
        Long memberId = 1L;
//        Long memberId = (Long)authentication.getPrincipal();
        return ResponseEntity.ok(orderService.orderSheet(memberId, variantId, optionId));
    }

}