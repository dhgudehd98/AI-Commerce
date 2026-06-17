package com.sh.aicommerce.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@NoArgsConstructor
@Getter
public class InboundItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "inbound_item_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inbound_id", nullable = false)
    private Inbound inbound;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_option_id" , nullable = false)
    private ProductOption productOption;

    @Column(nullable = false)
    private Integer quantity; // 입고 수

    private LocalDateTime inboundTime;

    public InboundItem(Inbound inbound, ProductOption productOption, Integer quantity, LocalDateTime inboundTime) {
        this.inbound = inbound;
        this.productOption = productOption;
        this.quantity = quantity;
        this.inboundTime = inboundTime;
    }
}