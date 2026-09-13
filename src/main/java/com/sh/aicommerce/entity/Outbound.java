package com.sh.aicommerce.entity;

import com.sh.aicommerce.wms.outbound.enums.OutboundStatus;
import com.sh.aicommerce.wms.outbound.enums.OutboundType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.query.Order;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@NoArgsConstructor
@Getter
@Table(
        name = "outbound",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_outbound_number", columnNames = "outbound_number")
        }
)
public class Outbound {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "outbound_id")
    private Long id;

    @Column(name = "outbound_number", nullable = false, unique = true)
    private String outboundNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboundType outBoundType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboundStatus outBoundStatus;

    @OneToOne(fetch =  FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Orders order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse; // 창고 번호

    @OneToMany(mappedBy = "outbound", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OutboundItem> outBoundItems = new ArrayList<>();

    @Column(nullable = false)
    private LocalDateTime createdAt; // 출고 요청 생성 시간

    private LocalDateTime updatedAt;

    private LocalDateTime deliveryRequestedAt; // 택배 인계 요청 시간 -> PACKING까지 완료하면 자동으로 택배 인계 요청하기

    private LocalDateTime completedAt; // 출고 완료 시간 -> 실제로 택배 인계 완료까지에 대한 시간

    @Column(nullable = false)
    private boolean cancellationRequest;

    private String outboundHoldReason;


    public static Outbound createOutboundByOrder(String outboundNumber, Orders order, Warehouse warehouse) {
        Outbound outbound = new Outbound();
        outbound.outboundNumber = outboundNumber;
        outbound.outBoundType = OutboundType.ORDER;
        outbound.outBoundStatus = OutboundStatus.PENDING;
        outbound.createdAt = LocalDateTime.now();
        outbound.warehouse = warehouse;
        outbound.cancellationRequest = false;
        outbound.order = order;

        for (OrderItem orderItem : order.getOrderItems()) {
            OutboundItem outboundItem = OutboundItem.create(orderItem, outbound);
            outbound.outBoundItems.add(outboundItem);
        }

        return outbound;
    }

    public void changeOutboundStatus(OutboundStatus outBoundStatus) {
        this.outBoundStatus = outBoundStatus;
    }
}