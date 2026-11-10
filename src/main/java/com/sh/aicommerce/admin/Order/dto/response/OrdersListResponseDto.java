package com.sh.aicommerce.admin.Order.dto.response;

import com.sh.aicommerce.enums.order.OrderStatus;
import com.sh.aicommerce.enums.payment.PaymentStatus;
import lombok.Getter;
import java.time.LocalDateTime;

@Getter
public class OrdersListResponseDto {
    private final Long orderId; // 타임라인 조회용 주문 ID
    private final String orderNumber; // 주문번호
    private final Long memberId; // 주문 회원 ID
    private final String memberName; // 주문 회원 이름
    private final Integer totalPrice; // 주문 총액
    private final String orderStatus; // 현재 주문 상태 코드
    private final String orderStatusLabel; // 주문 상태 표시명
    private final Long paymentId; // 결제 ID, 결제 준비 전에는 null 가능
    private final String paymentStatus; // 현재 결제 상태 코드
    private final String paymentStatusLabel; // 결제 상태 표시명
    private final boolean compensationRequired; // 보상 대상 여부
    private final LocalDateTime orderedAt; // 주문 생성 시각

    public OrdersListResponseDto(Long orderId, String orderNumber, Long memberId, String memberName,
                                 Integer totalPrice, OrderStatus orderStatus, Long paymentId,
                                 PaymentStatus paymentStatus, Boolean compensationRequired, LocalDateTime orderedAt) {
        this.orderId = orderId;
        this.orderNumber = orderNumber;
        this.memberId = memberId;
        this.memberName = memberName;
        this.totalPrice = totalPrice;
        this.orderStatus = orderStatus.name();
        this.orderStatusLabel = orderStatus.getStatus();
        this.paymentId = paymentId;
        this.paymentStatus = paymentStatus == null ? null : paymentStatus.name();
        this.paymentStatusLabel = paymentStatus == null ? null : paymentStatus.getStatus();
        this.compensationRequired = Boolean.TRUE.equals(compensationRequired);
        this.orderedAt = orderedAt;
    }
}
