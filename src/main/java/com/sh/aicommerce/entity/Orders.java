package com.sh.aicommerce.entity;

import com.sh.aicommerce.enums.order.OrderStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@NoArgsConstructor
@Getter
public class Orders {

    private static final DateTimeFormatter ORDER_NUMBER_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(nullable = false, unique = true)
    private String orderNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(nullable = false)
    private Integer totalPrice;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> orderItems = new ArrayList<>();

    @OneToOne(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private Payment payment;

    @OneToOne(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private Delivery delivery;

    private LocalDateTime orderedAt;

    public static Orders createOrder(Member member, Long optionId, Integer
            totalPaymentPrice) {
        Orders order = new Orders();
        order.member = member;
        order.orderNumber = createOrderNumber(optionId);
        order.status = OrderStatus.CREATED;
        order.totalPrice = totalPaymentPrice;
        order.orderedAt = LocalDateTime.now();

        return order;
    }

    private static String createOrderNumber(Long optionId) {
        String orderedAt = LocalDateTime.now().format(ORDER_NUMBER_FORMATTER);
        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 6)
                .toUpperCase();

        return "ORD-" + orderedAt + "-" + optionId + "-" + suffix;
    }

    public void setDelivery(Delivery delivery) {
        this.delivery = delivery;
        delivery.setOrder(this);
    }

    public void setPayment(Payment payment) {
        this.payment = payment;
        payment.setOrder(this);
    }

    public void addOrderItem(OrderItem item) {
        this.orderItems.add(item);
        item.setOrder(this);
    }

    public void updateStatus() {
        this.status = OrderStatus.PAID;
    }


    public void updateStatusFail() {
        this.status = OrderStatus.CANCELED; // 일단 실패로 설정
//        this.status = OrderStatus.FAILED;
    }
}