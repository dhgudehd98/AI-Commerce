package com.sh.aicommerce.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@NoArgsConstructor
@Getter
public class OutboundItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "outbound_item_id")
    private Long id;

    @ManyToOne(fetch =  FetchType.LAZY)
    @JoinColumn(name = "outbound_id", nullable = false)
    private Outbound outbound;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_item_id")
    private OrderItem orderItem;

    @Column(nullable = false)
    private Integer quantity; // 출고 수


    public static OutboundItem create(OrderItem orderItem, Outbound outbound) {
        OutboundItem outboundItem = new OutboundItem();
        outboundItem.outbound = outbound;
        outboundItem.orderItem = orderItem;
        outboundItem.quantity = orderItem.getQuantity();

        return outboundItem;
    }
}