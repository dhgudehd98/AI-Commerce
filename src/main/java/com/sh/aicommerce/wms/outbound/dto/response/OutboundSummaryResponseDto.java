package com.sh.aicommerce.wms.outbound.dto.response;

import com.sh.aicommerce.entity.Outbound;
import com.sh.aicommerce.entity.OutboundItem;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class OutboundSummaryResponseDto {
    private Long outboundId;
    private String outboundNumber;
    private String orderNumber;
    private String outboundStatus;
    private Integer totalQuantity;
    private LocalDateTime createdAt;
    private boolean cancellationRequested;

    public static OutboundSummaryResponseDto from(Outbound outbound) {
        int totalOutboundCnt = outbound.getOutBoundItems().stream()
                .mapToInt(OutboundItem::getQuantity)
                .sum();

        OutboundSummaryResponseDto outboundSummaryResponseDto = new OutboundSummaryResponseDto();
        outboundSummaryResponseDto.outboundId = outbound.getId();
        outboundSummaryResponseDto.outboundNumber = outbound.getOutboundNumber();
        outboundSummaryResponseDto.orderNumber = outbound.getOrder().getOrderNumber();
        outboundSummaryResponseDto.outboundStatus = outbound.getOutBoundStatus().getStatus();
        outboundSummaryResponseDto.totalQuantity = totalOutboundCnt;
        outboundSummaryResponseDto.createdAt = outbound.getCreatedAt();
        outboundSummaryResponseDto.cancellationRequested = outbound.isCancellationRequest();

        return outboundSummaryResponseDto;
    }
}