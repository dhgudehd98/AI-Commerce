package com.sh.aicommerce.orderCancellation.controller;

import com.sh.aicommerce.orderCancellation.dto.response.OrderCancelDetailResponseDto;
import com.sh.aicommerce.orderCancellation.dto.response.OrderCancelFailResponseDto;
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

    // 관리자 인증 연동 전 개발용 임시 관리자 ID
    private static final Long TEMP_ADMIN_MEMBER_ID = 3L;

    private final OrderCancellationService orderCancellationService;
    private final com.sh.aicommerce.orderCancellation.service.OrderCancellationInventoryService inventoryService;

    @PostMapping("/{cancelId}/item-return")
    public ResponseEntity<?> confirmItemReturn(@PathVariable Long cancelId) {
        inventoryService.confirmReturn(TEMP_ADMIN_MEMBER_ID, cancelId);
        return ResponseEntity.ok(java.util.Map.of("result", "Y"));
    }

    @PostMapping("/{cancelId}/inventory")
    public ResponseEntity<?> applyInventory(@PathVariable Long cancelId) {
        inventoryService.applyInventory(TEMP_ADMIN_MEMBER_ID, cancelId);
        return ResponseEntity.ok(java.util.Map.of("result", "Y"));
    }

    // 주문 취소 리스트 출력 API
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

    // 주문 취소 상세 정보 API
    @GetMapping("/detail/{cancelId}")
    public ResponseEntity<?> orderCancellationDetail(
            @PathVariable Long cancelId
    ) {
        Long memberId = 3L;
        return ResponseEntity.ok(orderCancellationService.orderCancellationDetail(cancelId));
    }



    //주문 취소 요청 실패 List API
    @GetMapping("fail")
    public String orderCancelFailList(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Model model
    ) {
        Long memberId = 3L;
        Page<OrderCancelFailResponseDto> cancelFailPages = orderCancellationService.orderCancelFailList(memberId, page, size);
        model.addAttribute("cancelFailPages", cancelFailPages);

        return "orderCancellation/orderCancelFailList";
    }

    @GetMapping("fail/{cancellationId}")
    public ResponseEntity<?> orderCancelFailedDetail(
            @PathVariable(name = "cancellationId") Long cancellationId
    ) {
        Long memberId = 3L;
        return ResponseEntity.ok(orderCancellationService.orderCancelFailedDetail(memberId, cancellationId));
    }

    // PG 요청 내역 조회 API
    @GetMapping("fail/{cancellationId}/pgPayment")
    public ResponseEntity<?> requestPaymentHistory(
            @PathVariable Long cancellationId
    ) {
        Long memberId = 3L;
        return ResponseEntity.ok(orderCancellationService.getPaymentRequestHistory(memberId, cancellationId));
    }

    // 결제 취소가 반

}