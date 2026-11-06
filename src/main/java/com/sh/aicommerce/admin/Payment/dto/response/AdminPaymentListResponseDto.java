package com.sh.aicommerce.admin.Payment.dto.response;

import com.sh.aicommerce.enums.payment.PaymentMethod;
import com.sh.aicommerce.enums.payment.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class AdminPaymentListResponseDto {
    private Long paymentId;                 // 결제 식별자: 결제 상세 조회에 사용
    private Long orderId;                   // 주문 식별자: 주문 상세 연결에 사용
    private String orderNumber;             // 관리자가 검색·식별할 주문번호
    private Long memberId;                  // 주문 회원 식별자
    private String memberName;              // 주문 회원 이름
    private Integer amount;                 // 결제 요청 금액: 미승인·취소 건에서도 원래 요청 금액 유지
    private String paymentStatus;           // 결제 상태 코드: PAID, CANCELED 등
    private String paymentStatusLabel;      // 화면에 표시할 결제 상태명
    private String paymentMethod;           // 결제 방법 코드: SAVED_CARD, SAVED_ACCOUNT, TOSS
    private String paymentMethodLabel;      // 화면에 표시할 결제 방법명
    private boolean compensationRequired;   // 취소 보상 대상 여부: PAID여도 보상 진행 중일 수 있음
    private LocalDateTime createdAt;        // 내부 결제 준비 내역 생성 시각
    private LocalDateTime paidAt;           // Payment의 승인 반영 시각, 미승인은 null. PG 거래 시각은 원장 occurredAt 참조

    public AdminPaymentListResponseDto(
            Long paymentId, Long orderId, String orderNumber, Long memberId, String memberName,
            Integer amount, PaymentStatus paymentStatus, PaymentMethod paymentMethod,
            String paymentMethodLabel, boolean compensationRequired,
            LocalDateTime createdAt, LocalDateTime paidAt) {
        this.paymentId = paymentId;
        this.orderId = orderId;
        this.orderNumber = orderNumber;
        this.memberId = memberId;
        this.memberName = memberName;
        this.amount = amount;
        this.paymentStatus = paymentStatus.name();
        this.paymentStatusLabel = paymentStatus.getStatus();
        this.paymentMethod = paymentMethod.name();
        this.paymentMethodLabel = paymentMethodLabel;
        this.compensationRequired = compensationRequired;
        this.createdAt = createdAt;
        this.paidAt = paidAt;
    }
}
