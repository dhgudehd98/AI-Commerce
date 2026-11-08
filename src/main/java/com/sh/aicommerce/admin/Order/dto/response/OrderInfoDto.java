package com.sh.aicommerce.admin.Order.dto.response;

import com.sh.aicommerce.entity.Orders;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class OrderInfoDto {
    private Long orderId;
    private String orderNumber; // 주문번호

    private Long memberId;
    private String memberName;

    private String orderStatus;
    private Integer totalPrice;
    private LocalDateTime orderedAt; // 주문 생성 시간

    private OrderItemResponseDto item;

    public OrderInfoDto(Orders order) {
        this.orderId = order.getId();
        this.orderNumber = order.getOrderNumber();
        this.memberId = order.getMember().getId();
        this.memberName = order.getMember().getMemberName();
        this.orderStatus = order.getStatus().name();
        this.totalPrice = order.getTotalPrice();
        this.orderedAt = order.getOrderedAt();
        // 현재 MVP는 주문당 상품 옵션 하나이며, 주문 당시 스냅샷을 표시합니다.
        this.item = order.getOrderItems().stream().findFirst().map(OrderItemResponseDto::new).orElse(null);
    }
}