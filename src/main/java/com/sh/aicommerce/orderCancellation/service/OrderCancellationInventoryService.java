package com.sh.aicommerce.orderCancellation.service;

import com.sh.aicommerce.admin.admin.service.AdminService;
import com.sh.aicommerce.common.exception.orderCancellation.OrderCancellationException;
import com.sh.aicommerce.entity.*;
import com.sh.aicommerce.enums.order.OrderCancelStatus;
import com.sh.aicommerce.enums.paymentCancellation.*;
import com.sh.aicommerce.enums.wms.*;
import com.sh.aicommerce.orderCancellation.repository.OrderCancellationRepository;
import com.sh.aicommerce.paymentCancellation.repository.PaymentCancellationRepository;
import com.sh.aicommerce.wms.inventory.repository.ProductInventoryRepository;
import com.sh.aicommerce.wms.outbound.repository.OutboundRepository;
import com.sh.aicommerce.wms.outbound.enums.OutboundStatus;
import com.sh.aicommerce.wms.stockMovement.repository.StockMovementRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderCancellationInventoryService {
    private final AdminService adminService;
    private final OrderCancellationRepository cancellations;
    private final PaymentCancellationRepository paymentCancellations;
    private final OutboundRepository outbounds;
    private final ProductInventoryRepository inventories;
    private final StockMovementRepository movements;
    private final EntityManager entityManager;
    private final com.sh.aicommerce.paymentCompensation.repository.PaymentCompensationRepository compensations;
    private final org.springframework.context.ApplicationEventPublisher events;

    @Transactional
    public void confirmReturn(Long adminId, Long cancellationId) {
        adminService.validateAdminMemberByMemberId(adminId);
        OrderCancellation c = lock(cancellationId);
        validatePayment(c);
        if (c.getItemReturnStatus() == ItemReturnStatus.RETURNED) return;
        validateProcessing(c);
        if (!c.isItemReturnRequired() || c.getItemReturnStatus() != ItemReturnStatus.WAITING) {
            throw new OrderCancellationException("상품 복귀 확인 대기 상태에서만 확인할 수 있습니다.");
        }
        c.confirmItemReturn(adminId.toString());
        log.info("[주문 취소 상품 복귀 확인] 취소 ID: {}, 관리자 ID: {}", cancellationId, adminId);
    }

    @Transactional
    public void applyInventory(Long adminId, Long cancellationId) {
        adminService.validateAdminMemberByMemberId(adminId);
        OrderCancellation c = lock(cancellationId);
        validatePayment(c);
        if (c.getInventoryStatus() == InventoryCancelStatus.COMPLETED) return;
        validateProcessing(c);
        if (c.getInventoryStatus() != InventoryCancelStatus.PENDING
                || !(c.getItemReturnStatus() == ItemReturnStatus.NOT_REQUIRED && !c.isItemReturnRequired()
                || c.getItemReturnStatus() == ItemReturnStatus.RETURNED)) {
            throw new OrderCancellationException("상품 복귀 불필요 또는 복귀 완료인 재고 처리 대기 건만 처리할 수 있습니다.");
        }
        Orders order = c.getOrder();
        if (order.getStatus() != com.sh.aicommerce.enums.order.OrderStatus.CANCEL_REQUEST) {
            throw new OrderCancellationException("취소 요청 상태의 주문만 재고를 반영할 수 있습니다.");
        }
        entityManager.lock(order, LockModeType.PESSIMISTIC_WRITE);
        Outbound outbound = outbounds.findOutboundByOrderIdWithLock(order.getId()).orElse(null);
        boolean compensationWithoutOutbound = outbound == null && order.getPayment().isCompensationRequired();
        if (outbound == null && !compensationWithoutOutbound) {
            throw new OrderCancellationException("출고 정보가 없습니다.");
        }
        List<StockMovement> deductions = movements.findByReferenceTypeAndReferenceId(StockMovementReferenceType.ORDER, order.getId())
                .stream().filter(m -> m.getStockMovementStatus() == StockMovementStatus.OUTBOUND).toList();
        boolean restore = !deductions.isEmpty();
        if (compensationWithoutOutbound && restore) {
            throw new OrderCancellationException("출고 정보 없이 차감 이력이 존재합니다. 창고 확인이 필요합니다.");
        }
        if (restore && deductions.stream().mapToInt(StockMovement::getQuantity).sum()
                != order.getOrderItems().stream().mapToInt(OrderItem::getQuantity).sum()) {
            throw new OrderCancellationException("주문 전체 수량과 실제 출고 차감 이력이 일치하지 않습니다.");
        }
        if (restore && c.getItemReturnStatus() != ItemReturnStatus.RETURNED) {
            throw new OrderCancellationException("실제 재고 복원은 상품 회수 및 검수 확인 후 가능합니다.");
        }
        if (!restore && outbound != null && !EnumSet.of(OutboundStatus.PENDING, OutboundStatus.ON_HOLD, OutboundStatus.PICKING,
                OutboundStatus.PICKED, OutboundStatus.PACKING, OutboundStatus.PACKED).contains(outbound.getOutBoundStatus())) {
            throw new OrderCancellationException("출고 차감 이력과 출고 상태를 확인해야 합니다.");
        }
        if (order.getOrderItems().isEmpty()) throw new OrderCancellationException("주문 상품이 없습니다.");
        // 모든 상품을 동일 순서로 잠가 동시 재고 변경 시 교착 가능성을 줄입니다.
        List<OrderItem> items = order.getOrderItems().stream()
                .sorted(Comparator.comparing(i -> i.getProductOption().getId())).toList();
        for (OrderItem item : items) {
            // 출고가 없는 보상 건은 기존 예약 경로와 같은 옵션 단위 조회를 사용합니다.
            // 여러 창고가 매칭되면 단일 조회가 실패하므로 임의 창고를 선택하지 않습니다.
            ProductInventory inventory = (outbound == null
                    ? inventories.findProductInventoryByOptionIdForUpdate(item.getProductOption().getId())
                    : inventories.findByProductOptionIdAndWarehouseIdForUpdate(
                            item.getProductOption().getId(), outbound.getWarehouse().getId()))
                    .orElseThrow(() -> new OrderCancellationException("예약 재고 정보를 확인할 수 없습니다."));
            if (restore) {
                int deducted = deductions.stream().filter(m -> Objects.equals(m.getProductInventory().getId(), inventory.getId()))
                        .mapToInt(StockMovement::getQuantity).sum();
                if (deducted != item.getQuantity()) throw new OrderCancellationException("실제 출고 차감 수량이 주문 수량과 일치하지 않습니다.");
            }
            int before = restore ? inventory.getOnHandQuantity() : inventory.getReservedQuantity();
            if (restore) inventory.increaseOnHandQuantity(item.getQuantity());
            else inventory.updateReleasedReservationQuantity(item.getQuantity());
            movements.save(new StockMovement(inventory,
                    restore ? StockMovementStatus.INBOUND : StockMovementStatus.RESERVATION_RELEASE,
                    StockMovementReferenceType.ORDER_CANCELLATION, c.getId(), item.getQuantity(), before,
                    restore ? inventory.getOnHandQuantity() : inventory.getReservedQuantity(), LocalDateTime.now()));
        }
        items.stream().map(i -> i.getProductOption().getProduct().getId()).distinct()
                .forEach(id -> events.publishEvent(new com.sh.aicommerce.product.redis.ProductIndexEventRecord(id, "INBOUND")));
        c.completeInventory(!restore, adminId.toString());
        order.updateStatusToCancel();
        if (outbound != null) outbound.updateStatusToCancelled();
        paymentCancellations.findByOrderCancellationId(c.getId()).ifPresent(pc ->
                compensations.findAllByPaymentCancellationId(pc.getId()).stream()
                        .filter(task -> task.getStatus() == com.sh.aicommerce.enums.paymentCompensation.PaymentCompensationStatus.CANCELLATION_PENDING)
                        .forEach(PaymentCompensation::complete));
        log.info("[주문 취소 재고 반영] 취소 ID: {}, 처리 유형: {}, 관리자 ID: {}", cancellationId,
                restore ? "실재고 복원" : "예약 해제", adminId);
    }

    private OrderCancellation lock(Long id) {
        return cancellations.findByIdForUpdate(id)
                .orElseThrow(() -> new OrderCancellationException("주문 취소 내역이 없습니다."));
    }

    private void validatePayment(OrderCancellation c) {
        PaymentCancellation pc = paymentCancellations.findPaymentCancellationByOrderCancellationIdForUpdate(c.getId())
                .orElseThrow(() -> new OrderCancellationException("결제 취소 작업이 없습니다."));
        if (pc.getCancelAmount() == null || !Objects.equals(pc.getCancelAmount(), c.getOrder().getPayment().getAmount())
                || pc.getStatus() != PaymentCancellationStatus.SUCCESS
                || !Objects.equals(pc.getPayment().getId(), c.getOrder().getPayment().getId())) {
            throw new OrderCancellationException("해당 주문의 결제 취소 성공 확인 후 처리할 수 있습니다.");
        }
    }

    private void validateProcessing(OrderCancellation c) {
        if (c.getCancelStatus() != OrderCancelStatus.PROCESSING) {
            throw new OrderCancellationException("처리 중인 주문 취소만 작업할 수 있습니다.");
        }
    }
}
