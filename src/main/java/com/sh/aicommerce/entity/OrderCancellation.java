package com.sh.aicommerce.entity;

import com.sh.aicommerce.enums.order.OrderCancelStatus;
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
    private LocalDateTime updatedAt; // 취소 상태 변경 될 떄마다 업데이트
    private LocalDateTime completedAt; // 취소 완료 시간

    public OrderCancellation(Orders order, String cancelReason, boolean itemReturnRequired) {
        this.order = order;
        this.cancelStatus = OrderCancelStatus.CANCEL_REQUEST;
        this.cancelReason = cancelReason;
        this.itemReturnRequired = itemReturnRequired;
        this.requestedAt = LocalDateTime.now();
    }
}