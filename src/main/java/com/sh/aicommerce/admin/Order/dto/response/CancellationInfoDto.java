package com.sh.aicommerce.admin.Order.dto.response;

import com.sh.aicommerce.entity.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

@Getter
@NoArgsConstructor
public class CancellationInfoDto {
    private Long orderCancellationId; // 주문 취소 ID
    private Long paymentCancellationId; // PG 취소 ID, 작업 생성 전에는 null
    private String orderCancelStatus; // 전체 주문 취소 상태
    private String orderCancelStatusLabel; // 주문 취소 상태 표시명
    private String paymentCancelStatus; // PG 취소 상태
    private String paymentCancelStatusLabel; // PG 취소 상태 표시명
    private Integer cancelAmount; // 취소 요청 금액, 완료 전에는 환불 금액으로 단정하지 않음
    private String cancelReason; // 주문 취소 사유
    private boolean itemReturnRequired; // 상품 복귀 필요 여부
    private String itemReturnStatus; // 상품 복귀 상태
    private String inventoryStatus; // 재고 후속 처리 상태
    private String orderFailCode; // 주문 취소 오류 코드
    private String orderFailMessage; // 주문 취소 오류 설명
    private String paymentFailCode; // PG 취소 오류 코드
    private String paymentFailMessage; // PG 취소 오류 설명
    private LocalDateTime requestedAt; // 주문 취소 요청 시각
    private LocalDateTime startedAt; // 기록된 주문 취소 시작 시각, 없으면 null
    private OffsetDateTime canceledAt; // 취소 원장 occurredAt: 실제 PG 취소 시각, 원장이 없으면 null
    private LocalDateTime itemReturnedAt; // 상품 복귀 확인 시각
    private LocalDateTime inventoryProcessedAt; // 재고 처리 완료 시각
    private LocalDateTime reservationReleasedAt; // 예약 해제 시각
    private LocalDateTime updatedAt; // 주문 취소 마지막 갱신 시각
    private LocalDateTime paymentCancellationUpdatedAt; // PG 취소 작업 마지막 갱신 시각
    private LocalDateTime completedAt; // 전체 주문 취소 완료 시각

    public CancellationInfoDto(OrderCancellation cancellation, PaymentCancellation pg, PaymentLedger cancellationLedger) {
        this.orderCancellationId = cancellation.getId();
        this.paymentCancellationId = pg == null ? null : pg.getId();
        this.orderCancelStatus = cancellation.getCancelStatus().name();
        this.orderCancelStatusLabel = cancellation.getCancelStatus().getStatus();
        this.paymentCancelStatus = pg == null ? null : pg.getStatus().name();
        this.paymentCancelStatusLabel = pg == null ? null : pg.getStatus().getDescription();
        this.cancelAmount = pg == null ? null : pg.getCancelAmount();
        this.cancelReason = cancellation.getCancelReason();
        this.itemReturnRequired = cancellation.isItemReturnRequired();
        this.itemReturnStatus = cancellation.getItemReturnStatus().name();
        this.inventoryStatus = cancellation.getInventoryStatus().name();
        this.orderFailCode = cancellation.getFailCode();
        this.orderFailMessage = cancellation.getFailMessage();
        this.paymentFailCode = pg == null ? null : pg.getFailCode();
        this.paymentFailMessage = pg == null ? null : pg.getFailMessage();
        this.requestedAt = cancellation.getRequestedAt();
        this.startedAt = cancellation.getStartedAt();
        this.canceledAt = cancellationLedger == null ? null : cancellationLedger.getOccurredAt();
        this.itemReturnedAt = cancellation.getItemReturnedAt();
        this.inventoryProcessedAt = cancellation.getInventoryProcessedAt();
        this.reservationReleasedAt = cancellation.getReservationReleasedAt();
        this.updatedAt = cancellation.getUpdatedAt();
        this.paymentCancellationUpdatedAt = pg == null ? null : pg.getUpdatedAt();
        this.completedAt = cancellation.getCompletedAt();
    }
}
