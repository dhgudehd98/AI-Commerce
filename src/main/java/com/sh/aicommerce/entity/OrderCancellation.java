package com.sh.aicommerce.entity;

import com.sh.aicommerce.enums.order.OrderCancelStatus;
import com.sh.aicommerce.enums.paymentCancellation.InventoryCancelStatus;
import com.sh.aicommerce.enums.paymentCancellation.ItemReturnStatus;
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InventoryCancelStatus inventoryStatus;
    private LocalDateTime inventoryProcessedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ItemReturnStatus itemReturnStatus;
    private boolean itemReturnRequired; // 상품 복귀 확인 필요 여부
    private LocalDateTime itemReturnedAt; // 상품 복귀 시간
    private LocalDateTime reservationReleasedAt; // 상품 선점 해제 완료 시간

    private String cancelReason; // 취소 사유
    private String failCode; //실패 코드
    private String failMessage; // 실패 사유
    private String admin; // 관리자
    private String paymentKey; // PG 결제 취소 요처

    @Column(nullable = false)
    private LocalDateTime requestedAt; // 취소 요청 시간
    private LocalDateTime startedAt; // 취소 처리 시작 시간
    private LocalDateTime updatedAt; // 취소 상태 변경 될 떄마다 업데이트
    private LocalDateTime completedAt; // 취소 완료 시간

    // 주문 취소 요청시, 생성자
    public OrderCancellation(Orders order, String cancelReason, boolean itemReturnRequired) {
        this.order = order;
        this.inventoryStatus = InventoryCancelStatus.PENDING;
        this.cancelStatus = OrderCancelStatus.REQUESTED;
        this.cancelReason = cancelReason;
        this.itemReturnRequired = itemReturnRequired;
        this.requestedAt = LocalDateTime.now();
    }

    // 주문 취소 스케줄러에서 해당 취소건 처리 시작
    public void startOrderCancellation() {
        this.updatedAt = LocalDateTime.now();
        this.cancelStatus = OrderCancelStatus.PROCESSING;
    }
}
