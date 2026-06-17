package com.sh.aicommerce.entity;

import com.sh.aicommerce.wms.outbound.enums.OutboundStatus;
import com.sh.aicommerce.wms.outbound.enums.OutboundType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

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

    @ManyToOne(fetch =  FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Orders order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse; // 창고 번호

    @OneToMany(mappedBy = "outbound", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OutboundItem> outBoundItems = new ArrayList<>();

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime completedAt;

    private String createdBy;
}