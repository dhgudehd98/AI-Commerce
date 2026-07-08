package com.sh.aicommerce.order.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Getter
public class OrderSheetPriceDto {

    private Integer productTotalPrice; // 상품 가격
    private Integer deliveryPrice; // 배송료
    private Integer discountAmount; // 할인 금액
    private Integer paymentAmount; // 총 결제 금액

    public OrderSheetPriceDto(Integer productTotalPrice, Integer deliveryPrice, Integer discountAmount, Integer paymentAmount) {
        this.productTotalPrice = productTotalPrice;
        this.deliveryPrice = deliveryPrice;
        this.discountAmount = discountAmount;
        this.paymentAmount = paymentAmount;
    }
}