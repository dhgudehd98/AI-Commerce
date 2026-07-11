package com.sh.aicommerce.enums.delivery;

public enum DeliveryStatus {
    CREATE, // 배송 생성
    PREPARING, // 배송준비
    SHIPPED, // 상품 배송중
    DELIVERED, // 상품 배달 완료
    CANCELED // 주문 취소
}