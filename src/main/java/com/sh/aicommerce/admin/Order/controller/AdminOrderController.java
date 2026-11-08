package com.sh.aicommerce.admin.Order.controller;

import com.sh.aicommerce.admin.Order.service.AdminOrderService;
import com.sh.aicommerce.admin.Order.dto.response.AdminOrderTimelineResponseDto;
import com.sh.aicommerce.admin.Order.dto.response.OrdersListResponseDto;
import org.springframework.data.domain.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

    private final AdminOrderService adminOrderService;

    @GetMapping("")
    public String page() {
        return "admin/order/orderList";
    }

    @GetMapping("/list")
    @ResponseBody
    public ResponseEntity<Page<OrdersListResponseDto>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(adminOrderService.ordersList(page, size));
    }

    @GetMapping("/{orderId}/timeline")
    @ResponseBody
    public ResponseEntity<AdminOrderTimelineResponseDto> getOrderTimeLine(
            @PathVariable Long orderId
    ){
        return ResponseEntity.ok(adminOrderService.getOrderTimeLine(orderId));
    }
}