package com.sh.aicommerce.toss.dto.cancel.request;

import com.sh.aicommerce.entity.Orders;
import com.sh.aicommerce.entity.Payment;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class TossCancelRequestDto {

    private String paymentKey;
    private String orderNumber;
    private String idempotencyKey;
    private String cancelReason;


    public TossCancelRequestDto(String paymentKey, String orderNumber, String idempotencyKey, String cancelReason) {
        this.paymentKey = paymentKey;
        this.orderNumber = orderNumber;
        this.idempotencyKey = idempotencyKey;
        this.cancelReason = cancelReason;
    }
}