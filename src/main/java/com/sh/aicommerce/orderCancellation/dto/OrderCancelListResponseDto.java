package com.sh.aicommerce.orderCancellation.dto;

import com.sh.aicommerce.entity.OrderCancellation;
import com.sh.aicommerce.entity.Outbound;
import com.sh.aicommerce.enums.order.OrderCancelStatus;
import com.sh.aicommerce.wms.outbound.enums.OutboundStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class OrderCancelListResponseDto {
    private Long cancelId; // 주문 취소 번호
    private Long orderId; // 주문 ID
    private String orderNumber; // 주문 번호
    private String outboundNumber; // 출고번호
    private Long outboundId; // 출고 번호
    private String outboundStatus; // 출고 상태
    private String cancelStatus; // 취소 상태
    private String cancelReason; // 실패 사유
    private LocalDateTime requestedAt; // 요청 시간
    private LocalDateTime updatedAt; // 취소 상태 업데이트 시간
    private LocalDateTime completedAt; // 완료시간
    private String admin; // 처리 관리자 값 없으면 -

    public OrderCancelListResponseDto(OrderCancellation oc, Outbound outbound, Long orderId, String orderNumber) {
        this.cancelId = oc.getId();
        this.orderId = orderId;
        this.orderNumber = orderNumber;
        this.outboundNumber = outbound.getOutboundNumber();
        this.outboundId = outbound.getId();
        this.outboundStatus = outbound.getOutBoundStatus().getStatus();
        this.cancelStatus = oc.getCancelStatus().getStatus();
        this.cancelReason = oc.getCancelReason();
        this.requestedAt = oc.getRequestedAt();
        this.updatedAt = oc.getUpdatedAt();
        this.completedAt = oc.getCompletedAt();
        this.admin = oc.getAdmin();
    }
}