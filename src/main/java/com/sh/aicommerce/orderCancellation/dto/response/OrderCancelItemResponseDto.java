package com.sh.aicommerce.orderCancellation.dto.response;

import com.sh.aicommerce.entity.OrderItem;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class OrderCancelItemResponseDto {
    private Long orderItemId;
    private String productName; // 주문 당시 상품 스냅샷
    private String brandName; // 주문 당시 브랜드 스냅샷
    private String sku; // 주문 당시 sku 스냅샷
    private Integer totalPrice; //lineTotalPrice;
    private Integer quantity; // 주문 개수
    private Long optionId; //
    private Integer unitPrice; // 주문 당시 단가

    public OrderCancelItemResponseDto(OrderItem orderItem) {
        this.orderItemId = orderItem.getId();
        this.productName = orderItem.getProductNameSnapshot();
        this.brandName = orderItem.getBrandNameSnapshot();
        this.sku = orderItem.getSkuSnapshot();
        this.totalPrice = orderItem.getLineTotalPrice();
        this.quantity = orderItem.getQuantity();
        this.optionId = orderItem.getProductOption().getId();
        this.unitPrice = orderItem.getUnitPrice();
    }
}