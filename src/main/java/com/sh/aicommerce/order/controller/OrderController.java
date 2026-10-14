package com.sh.aicommerce.order.controller;


import com.sh.aicommerce.order.dto.OrderCancelRequestDto;
import com.sh.aicommerce.order.dto.OrderSheetResponseDto;
import com.sh.aicommerce.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/order")
public class OrderController {

    private final OrderService orderService;


    @GetMapping("/sheet/{variantId}")
    public String orderSheet(
            @PathVariable Long variantId,
            @RequestParam Long optionId,
            Model model
//      Authentication authentication
    ) {
        Long memberId = 1L;
//  Long memberId = (Long) authentication.getPrincipal();

        OrderSheetResponseDto orderSheetResponseDto =
                orderService.orderSheet(memberId, variantId, optionId);

        model.addAttribute("orderSheet", orderSheetResponseDto);

        return "order/order-sheet-widget";
    }

    @PostMapping("cancel/{orderId}")
    public ResponseEntity<?> cancel(
            @RequestBody OrderCancelRequestDto requestDto,
            @PathVariable Long orderId
    ) {
        Long memberId = 1L;
        return ResponseEntity.ok(orderService.cancel(memberId, orderId, requestDto));
    }

}