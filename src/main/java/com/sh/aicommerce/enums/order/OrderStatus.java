package com.sh.aicommerce.enums.order;

import lombok.Getter;

@Getter
public enum OrderStatus {

    PENDING_PAYMENT("결제 대기"),     // 결제 대기: Orders 생성 및 재고 예약 완료, PG 승인 전

    PAYMENT_PROCESSING("결제 진행중"),  // 결제 진행 중: PG 승인 요청 중이거나 결과 확인이 필요한 상태

    PAID("결제 완료"),                // 결제 완료: PG 승인 성공 및 주문 확정

    PAYMENT_FAILED("결제 실패"),      // 결제 실패: PG 승인 실패가 확정된 상태

    EXPIRED("주문 만료"),             // 주문 만료: 결제 대기 시간 초과로 주문 종료 및 재고 예약 해제

    PREPARING("상품 준비"),           // 상품 준비 중: 결제 완료 후 판매자가 상품 준비

    SHIPPING("배송중"),            // 배송 중: 상품 발송 완료

    DELIVERED("배송 완료"),           // 배송 완료: 구매자에게 상품 전달 완료

    COMPLETED("구매 확정"),           // 구매 확정: 거래가 최종 완료된 상태
    CANCEL_REQUEST("주문 취소 요청"), // 사용자가 주문 취소 요청 처리

    CANCELING("주문 취소 진행중"),           // 주문 취소 진행 중: PG 결제 취소 및 재고 복구 처리 중

    CANCELED("주문 취소 완료"),             // 주문 취소 완료: 필요한 취소 및 재고 복구 처리 완료
    FAILED("취소"), //
    REFUNDED("주문 환불 완료");

    private String status;

    OrderStatus(String status) {
        this.status = status;
    }
}
