package com.sh.aicommerce.wms.outbound.dto.response;

import com.sh.aicommerce.entity.OutboundHistory;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter

@NoArgsConstructor

public class OutboundStatusResponseDto {
    private Long outboundId;
    private String outboundStatus;

    public OutboundStatusResponseDto(OutboundHistory history) {
        this.outboundId = history.getOutbound().getId();
        this.outboundStatus = history.getOutbound().getOutBoundStatus().getStatus();
    }

    public OutboundStatusResponseDto(Long outboundId, String outboundStatus) {
        this.outboundId = outboundId;
        this.outboundStatus = outboundStatus;
    }
}