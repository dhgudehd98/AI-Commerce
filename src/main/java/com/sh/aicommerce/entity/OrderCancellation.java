package com.sh.aicommerce.entity;

import com.sh.aicommerce.enums.order.OrderCancelStatus;
import com.sh.aicommerce.toss.dto.response.TossPaymentCancelResponseDto;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class OrderCancellation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_cancel_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", unique = true)
    private Orders order;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderCancelStatus cancelStatus;

    private String cancelReason; // 취소 사유
    private String failReason; // 실패 사유
    private String admin; // 관리자

    private boolean itemReturnRequired; // 상품 복귀 확인 필요 여부
    private LocalDateTime itemReturnedAt; // 상품 복귀 시간
    private LocalDateTime reservationReleasedAt; // 상품 선점 해제 완료 시간

    @Column(nullable = false)
    private LocalDateTime requestedAt; // 취소 요청 시간
    private LocalDateTime startedAt; // 취소 처리 시작 시간
    private LocalDateTime updatedAt; // 취소 상태 변경 될 떄마다 업데이트
    private LocalDateTime completedAt; // 취소 완료 시간

    public OrderCancellation(Orders order, String cancelReason, boolean itemReturnRequired) {
        this.order = order;
        this.cancelStatus = OrderCancelStatus.CANCEL_REQUEST;
        this.cancelReason = cancelReason;
        this.itemReturnRequired = itemReturnRequired;
        this.requestedAt = LocalDateTime.now();
    }

    public void startOrderCancel(String adminName) {
        this.cancelStatus = OrderCancelStatus.PROCESSING_START;
        this.startedAt = LocalDateTime.now();
        this.admin = adminName;
    }

    public void updateCancelStatus(OrderCancelStatus orderCancelStatus) {
        this.cancelStatus = orderCancelStatus;

    }

    public void updateCancelStatusToReservationReleased(OrderCancelStatus orderCancelStatus) {
        this.cancelStatus = orderCancelStatus;
        this.reservationReleasedAt = LocalDateTime.now();
    }

    public void updateCancelStatusToReservationReleaseFailed(OrderCancelStatus orderCancelStatus) {
        this.cancelStatus = orderCancelStatus;
    }

    public void updateCancelStatusToCancelFail(String failReason, OrderCancelStatus orderCancelStatus) {
        this.failReason = failReason;
        this.cancelStatus = orderCancelStatus;
        this.updatedAt = LocalDateTime.now();
    }

    public void updateCancelStatusToCancelFailCheck(String failReason, OrderCancelStatus orderCancelStatus) {
        this.failReason = failReason;
        this.cancelStatus = orderCancelStatus;
        this.updatedAt = LocalDateTime.now();
    }

    // 실제로 PG사 결제 취소 요청까지 성공적으로 진행된 경우
    public void updateCancelStatusToCancelFailCompleted() {
        this.cancelStatus = OrderCancelStatus.PAYMENT_CANCEL_COMPLETED;
        this.failReason = null;
        this.updatedAt = LocalDateTime.now();
    }

    //실제 주문취소의 모든 상태가 완료된 경우 CANCELED로 상태 변경 처리
    public void updateCancelStatusCanceled() {
        this.updatedAt = LocalDateTime.now();
        this.completedAt = LocalDateTime.now();
        this.cancelStatus = OrderCancelStatus.CANCELED; //
    }

    public void updateCancelStatusToPaymentCancelProcessing() {
        this.updatedAt = LocalDateTime.now();
        this.cancelStatus = OrderCancelStatus.PAYMENT_CANCEL_PROCESSING;
    }

    public void updateCancelStatusToItemReturnRequest() {
        this.updatedAt = LocalDateTime.now();
        this.cancelStatus = OrderCancelStatus.ITEM_RETURN_REQUEST;
    }

    public void updateCancelStatusItemReturnFailed(String failReason) {
        this.updatedAt = LocalDateTime.now();
        this.cancelStatus = OrderCancelStatus.ITEM_RETURN_FAILED;
        this.failReason = failReason;
    }
}
