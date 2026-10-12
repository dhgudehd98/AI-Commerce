package com.sh.aicommerce.orderCancellation.dto.response;

import com.sh.aicommerce.entity.Delivery;
import com.sh.aicommerce.enums.delivery.DeliveryCompany;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class OrderCancelDeliveryResponseDto {
    private String recipientName;
    private String recipientPhone;
    private String zipCode;
    private String address;
    private String addressDetail;
    private DeliveryCompany deliveryCompany;
    private String trackingNumber;

    public OrderCancelDeliveryResponseDto(Delivery delivery) {
        this.recipientName = delivery.getRecipientName();
        this.recipientPhone = delivery.getRecipientPhone();
        this.zipCode = delivery.getZipCode();
        this.address = delivery.getAddress();
        this.addressDetail = delivery.getAddressDetail();
        this.deliveryCompany = delivery.getDeliveryCompany();
        this.trackingNumber = delivery.getTrackingNumber();
    }
}