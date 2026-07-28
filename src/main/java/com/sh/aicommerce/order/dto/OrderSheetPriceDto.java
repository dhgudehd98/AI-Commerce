package com.sh.aicommerce.order.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Getter
public class OrderSheetPriceDto {

    private Integer productTotalPrice; // 구매가
    private Integer deliveryPrice; // 배송료
    private Integer couponAmount; // 쿠폰 사용
    private Integer pointAmount; // 포인트 사용
    private Integer paymentAmount; // 총 결제 금액

    public OrderSheetPriceDto(Integer productTotalPrice, Integer deliveryPrice, Integer couponAmount, Integer pointAmount, Integer paymentAmount) {
        this.productTotalPrice = productTotalPrice;
        this.deliveryPrice = deliveryPrice;
        this.couponAmount = couponAmount;
        this.pointAmount = pointAmount;
        this.paymentAmount = paymentAmount;
    }
}