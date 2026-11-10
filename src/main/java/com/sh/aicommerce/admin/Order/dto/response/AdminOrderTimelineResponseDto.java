package com.sh.aicommerce.admin.Order.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;


@Getter
@AllArgsConstructor
@NoArgsConstructor
public class AdminOrderTimelineResponseDto {
    private OrderInfoDto order; // 주문 내역
    private PaymentInfoDto payment; // 결제 내역
    private CancellationInfoDto cancellation; // 취소 내역
    private CompensationInfoDto compensation; // 보상 내역
}