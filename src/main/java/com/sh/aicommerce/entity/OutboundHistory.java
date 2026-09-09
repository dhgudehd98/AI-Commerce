package com.sh.aicommerce.entity;

import com.sh.aicommerce.wms.outbound.enums.OutboundHistoryActorType;
import com.sh.aicommerce.wms.outbound.enums.OutboundHistoryEventType;
import com.sh.aicommerce.wms.outbound.enums.OutboundStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 출고 작업의 변경 이력. 현재 출고·취소 상태를 대신하지 않는다.
 * 실패 이력은 실패한 업무 트랜잭션이 롤백된 이후 별도로 저장해야 한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "outbound_history", indexes = {
        @Index(name = "idx_outbound_history_outbound_created", columnList = "outbound_id, created_at")
})
public class OutboundHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "outbound_history_id")
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "outbound_id", nullable = false, updatable = false)
    private Outbound outbound;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private OutboundHistoryEventType eventType;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private OutboundStatus previousStatus;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private OutboundStatus nextStatus;

    // 개인정보나 예외 전문이 아닌 업무 사유 및 정제된 실패 원인을 저장한다.
    @Lob
    @Column(updatable = false)
    private String reason;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private OutboundHistoryActorType actorType;

    // 인증된 사용자 식별자 또는 시스템 작업 식별자. 요청 본문을 그대로 신뢰하지 않는다.
    @NotBlank
    @Column(nullable = false, updatable = false)
    private String actor;

    @NotNull
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 상태가 변하지 않은 취소 요청·실패는 전후 상태를 동일하게 기록한다. */
    public OutboundHistory(Outbound outbound, OutboundHistoryEventType eventType,
                           OutboundStatus previousStatus, OutboundStatus nextStatus,
                           String reason, OutboundHistoryActorType actorType,
                           String actor, LocalDateTime createdAt) {
        this.outbound = outbound;
        this.eventType = eventType;
        this.previousStatus = previousStatus;
        this.nextStatus = nextStatus;
        this.reason = reason;
        this.actorType = actorType;
        this.actor = actor;
        this.createdAt = createdAt;
    }
}
