package com.sh.aicommerce.orderCancellation.dto.response;

import com.sh.aicommerce.entity.*;
import com.sh.aicommerce.enums.order.OrderCancelStatus;
import com.sh.aicommerce.enums.paymentCancellation.*;
import com.sh.aicommerce.wms.outbound.enums.OutboundStatus;
import java.time.LocalDateTime;

public record OrderCancelInventoryResponseDto(boolean itemReturnRequired, ItemReturnStatus itemReturnStatusCode, String itemReturnStatus, LocalDateTime itemReturnedAt, InventoryCancelStatus inventoryStatusCode, String inventoryStatus, LocalDateTime inventoryProcessedAt, LocalDateTime reservationReleasedAt) {
    public OrderCancelInventoryResponseDto(OrderCancellation c) {
        this(c.isItemReturnRequired(), c.getItemReturnStatus(), c.getItemReturnStatus() == null ? null : c.getItemReturnStatus().getDescription(), c.getItemReturnedAt(), c.getInventoryStatus(), c.getInventoryStatus() == null ? null : c.getInventoryStatus().getDescription(), c.getInventoryProcessedAt(), c.getReservationReleasedAt());
    }
}
