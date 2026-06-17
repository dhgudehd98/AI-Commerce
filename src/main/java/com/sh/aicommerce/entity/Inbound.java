package com.sh.aicommerce.entity;

import com.sh.aicommerce.wms.inBound.dto.InboundType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@NoArgsConstructor
@Getter
@Table(
        name= "inbound",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_inbound_number", columnNames = "inbound_number")
        }
)
public class Inbound {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "inbound_id")
    private Long id;

    @Column(name = "inbound_number" ,nullable = false , unique = true)
    private String inboundNumber; // 입고 번호

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InboundType inboundType; // 최초 입고인지 / 재고 추가인지

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product; // 상품번호

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse; // 창고 번호

    @OneToMany(mappedBy = "inbound", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InboundItem> inboundItemList = new ArrayList<>();

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private String createdBy; // 누가 입고 처리를 했는지

    public Inbound(String inboundNumber, InboundType inboundType, Product product, Warehouse warehouse, LocalDateTime createdAt) {
        this.inboundNumber = inboundNumber;
        this.inboundType = inboundType;
        this.product = product;
        this.warehouse = warehouse;
        this.createdAt = createdAt;
        this.createdBy = "admin"; //! 임시로 admin으로 설정하고 나중에는 admin name에 대한 값으로 설정
    }
}