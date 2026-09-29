package com.sh.aicommerce.payment.service;

import com.sh.aicommerce.common.exception.orderCancellation.OrderCancellationException;
import com.sh.aicommerce.entity.OrderCancellation;
import com.sh.aicommerce.entity.Orders;
import com.sh.aicommerce.entity.Payment;
import com.sh.aicommerce.enums.order.OrderCancelStatus;
import com.sh.aicommerce.enums.payment.PaymentStatus;
import com.sh.aicommerce.orderCancellation.service.OrderCancellationService;
import com.sh.aicommerce.payment.repository.PaymentRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentCancellationPrerequisiteTest {
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final OrderCancellationService cancellations = mock(OrderCancellationService.class);
    private final PaymentTransactionService service = new PaymentTransactionService(
            payments, null, null, null, null, null, null, cancellations);

    @Test
    void releasedReservationDoesNotBypassRequiredItemReturn() {
        OrderCancellation cancellation = new OrderCancellation(null, "취소", true);
        cancellation.updateCancelStatusToReservationReleased(OrderCancelStatus.PRODUCT_RESERVATION_RELEASED);
        when(cancellations.validateOrderCancellationByIdWithLock(1L)).thenReturn(cancellation);

        assertThrows(OrderCancellationException.class, () -> service.findPaymentByCancellationId(1L));
        assertEquals(OrderCancelStatus.PRODUCT_RESERVATION_RELEASED, cancellation.getCancelStatus());
        verifyNoInteractions(payments);
    }

    @Test
    void itemReturnNotRequiredAllowsReleasedReservationToProceed() {
        Orders order = mock(Orders.class);
        when(order.getId()).thenReturn(2L);
        Payment payment = mock(Payment.class);
        when(payment.getStatus()).thenReturn(PaymentStatus.PAID);
        when(payments.findByOrderIdForUpdate(2L)).thenReturn(Optional.of(payment));
        OrderCancellation cancellation = new OrderCancellation(order, "취소", false);
        cancellation.updateCancelStatusToReservationReleased(OrderCancelStatus.PRODUCT_RESERVATION_RELEASED);
        when(cancellations.validateOrderCancellationByIdWithLock(1L)).thenReturn(cancellation);

        assertSame(payment, service.findPaymentByCancellationId(1L));
        assertEquals(OrderCancelStatus.PAYMENT_CANCEL_PROCESSING, cancellation.getCancelStatus());
    }

    @Test
    void missingReservationReleasePreventsPaymentCancellation() {
        OrderCancellation cancellation = new OrderCancellation(null, "취소", false);
        cancellation.updateCancelStatus(OrderCancelStatus.PRODUCT_RESERVATION_RELEASED);
        when(cancellations.validateOrderCancellationByIdWithLock(1L)).thenReturn(cancellation);

        assertThrows(OrderCancellationException.class, () -> service.findPaymentByCancellationId(1L));
        verifyNoInteractions(payments);
    }

    @Test
    void successfulCancellationClearsPreviousFailureReason() {
        OrderCancellation cancellation = new OrderCancellation(null, "취소", false);
        cancellation.updateCancelStatusToCancelFail("이전 실패", OrderCancelStatus.PAYMENT_CANCEL_FAILED);

        cancellation.updateCancelStatusToCancelFailCompleted(null, 1L, OrderCancelStatus.PAYMENT_CANCEL_COMPLETED);

        assertEquals(OrderCancelStatus.PAYMENT_CANCEL_COMPLETED, cancellation.getCancelStatus());
        assertNull(cancellation.getFailReason());
    }
    @Test
    void validatesFullCancellationResponseAndRejectsMissingAmounts() {
        com.sh.aicommerce.toss.dto.response.TossPaymentCancelResponseDto response =
                mock(com.sh.aicommerce.toss.dto.response.TossPaymentCancelResponseDto.class);
        com.sh.aicommerce.toss.dto.response.CancelResponseDto cancel =
                mock(com.sh.aicommerce.toss.dto.response.CancelResponseDto.class);
        Payment payment = mock(Payment.class);
        Orders order = mock(Orders.class);
        when(payment.getPaymentKey()).thenReturn("payment-key");
        when(payment.getAmount()).thenReturn(10000);
        when(order.getOrderNumber()).thenReturn("order-number");
        when(response.getPaymentKey()).thenReturn("payment-key");
        when(response.getOrderId()).thenReturn("order-number");
        when(response.getTotalAmount()).thenReturn(10000);
        when(response.getBalanceAmount()).thenReturn(0);
        when(response.getStatus()).thenReturn("CANCELED");
        when(response.getCancels()).thenReturn(java.util.List.of(cancel));
        when(response.getLastTransactionKey()).thenReturn("transaction-key");
        when(cancel.getTransactionKey()).thenReturn("transaction-key");
        when(cancel.getCancelStatus()).thenReturn("DONE");
        when(cancel.getCancelAmount()).thenReturn(10000);
        when(cancel.getCanceledAt()).thenReturn(java.time.OffsetDateTime.now());

        Runnable validate = () -> org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                service, "validateOrderCancelResponse", response, payment, order);
        assertDoesNotThrow(validate::run);
        when(response.getTotalAmount()).thenReturn(null);
        assertThrows(com.sh.aicommerce.common.exception.payment.PaymentException.class, validate::run);
        when(response.getTotalAmount()).thenReturn(10000);
        when(response.getBalanceAmount()).thenReturn(null);
        assertThrows(com.sh.aicommerce.common.exception.payment.PaymentException.class, validate::run);
        when(response.getBalanceAmount()).thenReturn(1);
        assertThrows(com.sh.aicommerce.common.exception.payment.PaymentException.class, validate::run);
        when(response.getBalanceAmount()).thenReturn(0);
        when(cancel.getCancelAmount()).thenReturn(9000);
        assertThrows(com.sh.aicommerce.common.exception.payment.PaymentException.class, validate::run);
        when(cancel.getCancelAmount()).thenReturn(10000);
        when(response.getCancels()).thenReturn(java.util.List.of());
        assertThrows(com.sh.aicommerce.common.exception.payment.PaymentException.class, validate::run);
    }
}
