package com.sh.aicommerce.orderCancellation.dto.response;

import com.sh.aicommerce.entity.*;
import com.sh.aicommerce.enums.order.OrderCancelStatus;
import com.sh.aicommerce.enums.paymentCancellation.*;

public record OrderCancelActionsResponseDto(boolean canConfirmItemReturn, boolean canApplyInventory) {
    public static OrderCancelActionsResponseDto from(OrderCancellation c, PaymentCancellation pc) {
        boolean processing = c.getCancelStatus() == OrderCancelStatus.PROCESSING;
        boolean paidCancel = pc != null && pc.getStatus() == PaymentCancellationStatus.SUCCESS;
        boolean pending = c.getInventoryStatus() == InventoryCancelStatus.PENDING;
        return new OrderCancelActionsResponseDto(
                processing && paidCancel && pending && c.isItemReturnRequired() && c.getItemReturnStatus() == ItemReturnStatus.WAITING,
                processing && paidCancel && pending && (c.getItemReturnStatus() == ItemReturnStatus.RETURNED
                        || !c.isItemReturnRequired() && c.getItemReturnStatus() == ItemReturnStatus.NOT_REQUIRED));
    }
}
