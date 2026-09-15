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
    private OrderCancelStatus cancelStatus;

    private String cancelReason; // 취소 사유
    private String admin; // 관리자

    private LocalDateTime requestedAt; // 취소 요청 시간
    private LocalDateTime completedAt; // 취소 완료 시간

    public OrderCancellation(Orders order, String cancelReason) {
        this.order = order;
        this.cancelStatus = OrderCancelStatus.CANCEL_REQUEST;
        this.cancelReason = cancelReason;
        this.requestedAt = LocalDateTime.now();
    }
}