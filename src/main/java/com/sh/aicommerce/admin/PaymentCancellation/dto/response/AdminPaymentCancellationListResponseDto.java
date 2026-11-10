package com.sh.aicommerce.admin.PaymentCancellation.dto.response;

import com.sh.aicommerce.enums.paymentCancellation.InventoryCancelStatus;
import com.sh.aicommerce.enums.order.OrderCancelStatus;
import com.sh.aicommerce.enums.paymentCancellation.PaymentCancellationStatus;
import com.sh.aicommerce.enums.payment.PaymentStatus;
import lombok.Getter;
import java.time.LocalDateTime;

@Getter
public class AdminPaymentCancellationListResponseDto {
    private final Long paymentCancellationId; // PG 결제 취소 작업 ID
    private final Long paymentId; // 원본 결제 ID
    private final Long orderCancellationId; // 주문 취소 ID
    private final String orderNumber; // 주문번호
    private final Long memberId; // 회원 ID
    private final String memberName; // 주문 회원 이름
    private final Integer amount; // 원본 결제 요청 금액
    private final Integer cancelAmount; // 취소 요청 금액: 성공 전에는 실제 환불 완료 금액이 아님
    private final String cancellationStatus; // PG 결제 취소 상태
    private final String cancellationStatusLabel; // 화면 표시명, 값이 없으면 null
    private final String paymentStatus; // 현재 결제 상태
    private final String paymentStatusLabel; // 화면 표시명, 값이 없으면 null
    private final String orderCancellationStatus; // 상품 복귀·재고 처리를 포함한 주문 취소 상태
    private final String orderCancellationStatusLabel; // 화면 표시명, 값이 없으면 null
    private final String inventoryStatus; // 취소 후 재고 처리 상태
    private final String inventoryStatusLabel; // 화면 표시명, 값이 없으면 null
    private final String cancelReason; // 취소 요청 사유
    private final String failCode; // 마지막 취소 오류 코드
    private final String failMessage; // 마지막 취소 오류 메시지
    private final LocalDateTime requestedAt; // 연결된 주문 취소 요청 시각
    private final LocalDateTime canceledAt; // PG 취소 완료 시각: 미확정이면 null
    private final LocalDateTime updatedAt; // PG 취소 작업 마지막 갱신 시각

    public AdminPaymentCancellationListResponseDto(
            Long paymentCancellationId,
            Long paymentId,
            Long orderCancellationId,
            String orderNumber,
            Long memberId,
            String memberName,
            Integer amount,
            Integer cancelAmount,
            PaymentCancellationStatus cancellationStatus,
            PaymentStatus paymentStatus,
            OrderCancelStatus orderCancellationStatus,
            InventoryCancelStatus inventoryStatus,
            String cancelReason,
            String failCode,
            String failMessage,
            LocalDateTime requestedAt,
            LocalDateTime canceledAt,
            LocalDateTime updatedAt) {
        this.paymentCancellationId = paymentCancellationId;
        this.paymentId = paymentId;
        this.orderCancellationId = orderCancellationId;
        this.orderNumber = orderNumber;
        this.memberId = memberId;
        this.memberName = memberName;
        this.amount = amount;
        this.cancelAmount = cancelAmount;
        this.cancellationStatus = cancellationStatus == null ? null : cancellationStatus.name();
        this.cancellationStatusLabel = cancellationStatus == null ? null : cancellationStatus.getDescription();
        this.paymentStatus = paymentStatus == null ? null : paymentStatus.name();
        this.paymentStatusLabel = paymentStatus == null ? null : paymentStatus.getStatus();
        this.orderCancellationStatus = orderCancellationStatus == null ? null : orderCancellationStatus.name();
        this.orderCancellationStatusLabel = orderCancellationStatus == null ? null : orderCancellationStatus.getStatus();
        this.inventoryStatus = inventoryStatus == null ? null : inventoryStatus.name();
        this.inventoryStatusLabel = inventoryStatus == null ? null : inventoryStatus.getDescription();
        this.cancelReason = cancelReason;
        this.failCode = failCode;
        this.failMessage = failMessage;
        this.requestedAt = requestedAt;
        this.canceledAt = canceledAt;
        this.updatedAt = updatedAt;
    }
}
