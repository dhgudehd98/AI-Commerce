package com.sh.aicommerce.admin.Order.dto.response;

import com.sh.aicommerce.entity.OrderItem;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class OrderItemResponseDto {
    private Long orderItemId;
    private String productName;
    private String brandName;
    private Integer basePrice;
    private Integer additionalPrice;
    private Integer totalPrice;

    public OrderItemResponseDto(OrderItem item) {
        this.orderItemId = item.getId();
        this.productName = item.getProductNameSnapshot();
        this.brandName = item.getBrandNameSnapshot();
        this.basePrice = item.getBasePriceSnapshot();
        this.additionalPrice = item.getAdditionalPriceSnapshot();
        this.totalPrice = item.getLineTotalPrice();
    }
}