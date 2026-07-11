package com.sh.aicommerce.entity;

import com.sh.aicommerce.enums.delivery.DeliveryCompany;
import com.sh.aicommerce.enums.delivery.DeliveryStatus;
import com.sh.aicommerce.order.dto.Receiver;
import com.sh.aicommerce.payment.dto.request.PaymentRequestDto;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;


@Entity
@NoArgsConstructor
@Getter
public class Delivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "delivery_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Orders order;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliveryStatus deliveryStatus;

    @Column(nullable = false)
    private String recipientName;

    @Column(nullable = false)
    private String recipientPhone;

    @Column(nullable = false)
    private String zipCode;

    @Column(nullable = false)
    private String address;

    @Column(nullable = false)
    private String addressDetail;

    private DeliveryCompany deliveryCompany;

    private String trackingNumber;

    private LocalDateTime createdAt;

    private LocalDateTime shippedAt;

    private LocalDateTime deliveredAt;

    public void setOrder(Orders order) {
        this.order = order;
    }

    public static Delivery createDelivery(Receiver receiver) {
        Delivery delivery = new Delivery();
        delivery.deliveryStatus = DeliveryStatus.CREATE;
        delivery.recipientName = receiver.getName();
        delivery.recipientPhone = receiver.getPhone();
        delivery.zipCode = receiver.getZipCode();
        delivery.address = receiver.getAddress();
        delivery.addressDetail = receiver.getAddressDetail();
        delivery.createdAt = LocalDateTime.now();

        return delivery;
    }
}
