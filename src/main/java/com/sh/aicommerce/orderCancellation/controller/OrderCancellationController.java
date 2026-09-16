package com.sh.aicommerce.orderCancellation.controller;

import com.sh.aicommerce.orderCancellation.dto.response.OrderCancelListResponseDto;
import com.sh.aicommerce.orderCancellation.service.OrderCancellationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/api/admin/cancel")
@RequiredArgsConstructor
public class OrderCancellationController {

    private final OrderCancellationService orderCancellationService;

    @GetMapping("")
    public String orderCancelList(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Model model
    ) {
        Long memberId = 3L;
        Page<OrderCancelListResponseDto> orderCancelPages = orderCancellationService.getOrderCancelList(3L, page, size);
        model.addAttribute("orderCancelPages", orderCancelPages);
        return "orderCancellation/orderCancellationList";
    }

    @PostMapping("{cancelId}")
    public ResponseEntity<?> cancel(
            @PathVariable Long cancelId
    ) {
        Long memberId = 3L;
        return ResponseEntity.ok(orderCancellationService.cancel(cancelId));
    }
}