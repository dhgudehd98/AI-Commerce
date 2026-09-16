package com.sh.aicommerce.orderCancellation.dto.response;

import com.sh.aicommerce.entity.OrderCancellation;
import com.sh.aicommerce.entity.OrderItem;
import com.sh.aicommerce.entity.Orders;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class OrderCancelOrderResponseDto {
    private LocalDateTime orderedAt;
    private Integer totalPrice;
    private List<OrderCancelItemResponseDto> items;

    public OrderCancelOrderResponseDto(Orders order, List<OrderItem> orderItems) {

        this.orderedAt = order.getOrderedAt();
        this.totalPrice = order.getTotalPrice();
        this.items = orderItems.stream()
                .map(orderItem -> new OrderCancelItemResponseDto(orderItem))
                .toList();
    }
}