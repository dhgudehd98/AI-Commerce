package com.sh.aicommerce.order.dto;


import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
public class OrderSheetResponseDto {

    // 받는 사람 정보
    private Receiver receiver;
    // OrderSheet 상품 정보
    private OrderSheetProductDto orderSheetProductDto;
    // OrderSheet 가격 정보
    private OrderSheetPriceDto orderSheetPriceDto;
    private String customerKey;
    private String orderNumber;


    public OrderSheetResponseDto(Receiver receiver, OrderSheetProductDto orderSheetProductDto, OrderSheetPriceDto orderSheetPriceDto, String customerKey, String orderNumber) {
        this.receiver = receiver;
        this.orderSheetProductDto = orderSheetProductDto;
        this.orderSheetPriceDto = orderSheetPriceDto;
        this.customerKey = customerKey;
        this.orderNumber = orderNumber;
    }
}