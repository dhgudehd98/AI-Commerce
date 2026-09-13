package com.sh.aicommerce.wms.outbound.dto.response;

import com.sh.aicommerce.entity.Delivery;
import com.sh.aicommerce.entity.Orders;
import com.sh.aicommerce.entity.Outbound;
import com.sh.aicommerce.enums.delivery.DeliveryCompany;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class OutboundDetailResponseDto {
    // 전체 출고상품

    private String recipientName;
    private String recipientPhone;
    private String zipCode;
    private String address;
    private String addressDetail;
    private DeliveryCompany deliveryCompany;
    private String trackingNumber;
    private LocalDateTime deliveryRequestedAt;
    private LocalDateTime completedAt;
    private String holdReason;

    public OutboundDetailResponseDto(Outbound outbound, Orders order, Delivery delivery) {
        this.recipientName = delivery.getRecipientName();
        this.recipientPhone = delivery.getRecipientPhone();
        this.zipCode = delivery.getZipCode();
        this.address = delivery.getAddress();
        this.addressDetail = delivery.getAddressDetail();
        this.deliveryCompany = delivery.getDeliveryCompany();
        this.trackingNumber = delivery.getTrackingNumber();
        this.deliveryRequestedAt = outbound.getDeliveryRequestedAt();
        this.completedAt = outbound.getCompletedAt();
        this.holdReason = outbound.getOutboundHoldReason();
    }

    //    private OutboundCancellationSummaryDto cancellation
}