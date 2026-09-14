package com.sh.aicommerce.wms.outbound.dto.response;

import com.sh.aicommerce.entity.OutboundHistory;
import com.sh.aicommerce.wms.outbound.enums.OutboundHistoryActorType;
import com.sh.aicommerce.wms.outbound.enums.OutboundHistoryEventType;
import com.sh.aicommerce.wms.outbound.enums.OutboundType;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class OutboundHistoryResponseDto {
    private Long id;
    private String eventType;
    private String previousStatus;
    private String currentStatus;
    private OutboundHistoryActorType actorType;
    private String actor;
    private String reason;
    private LocalDateTime createdAt;

    public OutboundHistoryResponseDto(OutboundHistory history) {
        this.id = history.getId();
        this.eventType = history.getEventType().getType();
        this.previousStatus = history.getPreviousStatus().getStatus();
        this.currentStatus = history.getCurrentStatus().getStatus();
        this.actorType = history.getActorType();
        this.actor = history.getActor();
        this.reason = history.getReason();
        this.createdAt = history.getCreatedAt();
    }
}