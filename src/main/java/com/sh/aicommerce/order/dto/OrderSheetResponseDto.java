package com.sh.aicommerce.order.dto;


import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
public class OrderSheetResponseDto {

    // 받는 사람 정보
    Receiver receiver;
    // OrderSheet 상품 정보
    OrderSheetProductDto orderSheetProductDto;
    // OrderSheet 가격 정보
    OrderSheetPriceDto orderSheetPriceDto;
    String customerKey;

    public OrderSheetResponseDto(Receiver receiver, OrderSheetProductDto orderSheetProductDto, OrderSheetPriceDto orderSheetPriceDto, String customerKey) {
        this.receiver = receiver;
        this.orderSheetProductDto = orderSheetProductDto;
        this.orderSheetPriceDto = orderSheetPriceDto;
        this.customerKey = customerKey;
    }
}