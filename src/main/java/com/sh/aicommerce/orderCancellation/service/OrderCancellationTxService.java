package com.sh.aicommerce.orderCancellation.service;

import com.sh.aicommerce.common.exception.order.OrderException;
import com.sh.aicommerce.common.exception.orderCancellation.OrderCancellationException;
import com.sh.aicommerce.common.exception.payment.PaymentException;
import com.sh.aicommerce.common.exception.refund.RefundException;
import com.sh.aicommerce.common.exception.wms.OutboundException;
import com.sh.aicommerce.entity.*;
import com.sh.aicommerce.enums.order.OrderCancelStatus;
import com.sh.aicommerce.enums.order.OrderStatus;
import com.sh.aicommerce.enums.payment.PaymentStatus;
import com.sh.aicommerce.enums.refund.RefundStatus;
import com.sh.aicommerce.orderCancellation.dto.record.OrderCancellationRetryRecord;
import com.sh.aicommerce.orderCancellation.repository.OrderCancellationRepository;
import com.sh.aicommerce.payment.repository.PaymentRepository;
import com.sh.aicommerce.refund.repository.RefundRepository;
import com.sh.aicommerce.toss.dto.response.TossPaymentRequestHistoryResponseDto;
import com.sh.aicommerce.toss.repository.TossPaymentLogRepository;
import com.sh.aicommerce.wms.outbound.enums.OutboundStatus;
import com.sh.aicommerce.wms.outbound.repository.OutboundRepository;
import com.sh.aicommerce.wms.outboundHistory.repository.OutboundHistoryRepository;
import java.util.Objects;
import java.util.Set;
import com.sh.aicommerce.wms.outbound.enums.OutboundHistoryEventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderCancellationTxService {

    private final OrderCancellationRepository orderCancellationRepository;
    private final RefundRepository refundRepository;
    private final PaymentRepository paymentRepository;
    private final OutboundRepository outboundRepository;
    private final OutboundHistoryRepository historyRepository;
    private final TossPaymentLogRepository paymentLogRepository;

    @Transactional
    public OrderCancellationRetryRecord getRetryRecordForCancelRetry(Long cancellationId) {
        return
                orderCancellationRepository
                        .findOrderCancellationForCancelRetry(cancellationId)
                        .orElseThrow(() -> new OrderCancellationException(
                                "[주문 취소 재처리 검증] 취소 건이 존재하지 않습니다."
                        ));
    }

    /**
     * 주문 취소 재처리 요청을 진행 할 때 검증해야 하는 데이터
     * <p>
     * - OrderCancellation
     * - Refund
     * - Outbound
     * - Orders
     * - Refund
     */
    @Transactional
    public OrderCancellationRetryRecord validateRetryDataForCancel(Long cancellationId) {

        log.info("[실패 된 주문 취소 요청 재처리] 데이터 검증 시작 주문 취소 번호 : {}", cancellationId);

        OrderCancellationRetryRecord data =
                orderCancellationRepository
                        .findOrderCancellationForCancelRetry(cancellationId)
                        .orElseThrow(() -> new OrderCancellationException(
                                "[주문 취소 재처리 검증] 취소 건이 존재하지 않습니다."
                        ));

        OrderCancellation cancellation = data.cancellation();
        Refund refund = data.refund();
        Orders order = data.order();
        Outbound outbound = data.outbound();
        Payment payment = data.payment();

        OrderCancelStatus cancelStatus = cancellation.getCancelStatus();
        RefundStatus refundStatus = refund.getRefundStatus();
        OrderStatus orderStatus = order.getStatus();
        OutboundStatus outboundStatus = outbound.getOutBoundStatus();
        PaymentStatus paymentStatus = payment.getStatus();

        if (order == null || payment == null
                || outbound == null || refund == null) {
            throw new OrderCancellationException(
                    "[주문 취소 재처리 검증] 주문·결제·출고·환불 연결 정보가 누락되었습니다."
            );
        }

        if (refund.getPayment() == null
                || !payment.getId().equals(refund.getPayment().getId())) {
            throw new OrderCancellationException(
                    "[주문 취소 재처리 검증] 환불에 연결된 결제 정보가 일치하지 않습니다."
            );
        }


        if(cancellation.getReservationReleasedAt() == null)
            throw new OrderCancellationException("주문 취소 재처리를 하기 위해서는 선점 재고 예약 해제를 먼저 진행해야 합니다. 주문 취소 번호 : " + cancellation.getId());

        // 상품 복귀 확인이 필요로 하고, 상품 복귀 시간이 null 값으로 지정되어 있는 경우에는 주문 취소 재처리 불가
        if(cancellation.isItemReturnRequired()) {
            if (cancellation.getItemReturnedAt() == null) {
                throw new OrderCancellationException("상품 복귀 확인 먼저 진행 후 주문 취소 재처리가 가능합니다. 주문 취소 번호 : " + cancellation.getId());
            }
        }

        if(cancelStatus != OrderCancelStatus.PAYMENT_CANCEL_FAILED && cancelStatus != OrderCancelStatus.PAYMENT_CANCEL_FAILED_CHECK)
            throw new OrderCancellationException("현재 주문 취소 재처리를 할 수 있는 상태가 아닙니다.");

        if(refundStatus != RefundStatus.FAILED && refundStatus != RefundStatus.FAILED_CHECK)
            throw new RefundException("현재 재처리 할 수 있는 환불 상태가 아닙니다. 관리자에게 문의해주세요. 환불 내역 번호 : " + refund.getId());

        // 출고에 대한 경우에는 취소 요청이 오지 않거나 취소된 출고 내역이 아니면 취소 재처리 불가
        if(!outbound.isCancellationRequest())
            throw new OutboundException("주문 취소 요청이 오지 않은 출고건입니다. 출고 번호를 확인해주세요. 출고 번호 : " + outbound.getId());

        if(outboundStatus == OutboundStatus.CANCELED)
            throw new OutboundException("이미 출고 취소가 완료된 주문 번호입니다. 관리자에게 문의해주세요.");

        if (orderStatus == OrderStatus.CANCELED) {
            throw new OrderException("이미 취소 처리가 완료된 주문입니다. 주문 취소 번호를 다시 확인해주세요. 주문 번호 : " + order.getId());
        }

        if (paymentStatus != PaymentStatus.CANCEL_FAILED) {
            throw new PaymentException("취소 실패 건만 취소 재처리 할 수 있습니다. 지불 내역 번호 : " + payment.getId());
        }

        return data;
    }

    /**
     * PG에서는 이미 취소 요청이 완료되었으며, 주문 취소와 관련 Entity들 상태 값 업데이트
     * - Refund
     * - OrderCancellation
     * - Outbound
     * - Payment
     *
     * 1. OrderCancellation → Payment → Refund 잠금 조회
     * 2. 최신 상태와 선행 조건 재검증
     * 3. PG 결제 식별자·주문번호·취소 상태·금액·거래 정보 검증
     * 4. 기존 Refund에 실제 취소 금액·거래 식별자·취소 시각 저장
     * 5. Payment → CANCELED
     * 6. Orders·Outbound·OrderCancellation → CANCELED
     * 7. PG 로그 중복 없이 저장
     */
    @Transactional
    public void applyRecoveredUpdateStatus(Long cancellationId, TossPaymentRequestHistoryResponseDto paymentRequest) {

        // 동일 취소 건의 복구는 취소 행 잠금으로 직렬화한다.
        OrderCancellation cancellation = orderCancellationRepository.findByIdForUpdate(cancellationId)
                .orElseThrow(() -> new OrderCancellationException("[취소 복구] 취소 정보 없음: " + cancellationId));
        Orders order = cancellation.getOrder();
        if (order == null) throw new OrderCancellationException("[취소 복구] 주문 연결 정보 없음");
        Payment payment = paymentRepository.findPaymentByOrderIdWithLock(order.getId())
                .orElseThrow(() -> new PaymentException("[취소 복구] 결제 정보 없음"));
        Refund refund = refundRepository.findByOrderCancellationIdWithLock(cancellationId)
                .orElseThrow(() -> new RefundException("[취소 복구] 환불 정보 없음"));
        Outbound outbound = outboundRepository.findOutboundByOrderIdWithLock(order.getId())
                .orElseThrow(() -> new OutboundException("[취소 복구] 출고 정보 없음"));

        validateRecoveredPayment(paymentRequest, payment, refund, order);
        if (refund.getPayment() == null || !Objects.equals(refund.getPayment().getId(), payment.getId())
                || cancellation.getReservationReleasedAt() == null
                || (cancellation.isItemReturnRequired() && cancellation.getItemReturnedAt() == null)
                || !outbound.isCancellationRequest()) {
            throw new OrderCancellationException("[취소 복구 검증] 결제 연결 또는 선행 작업 불일치: " + cancellationId);
        }

        TossPaymentLog existingLog = paymentLogRepository.findLogByCancellationIdWithLock(cancellationId).orElse(null);
        var cancel = paymentRequest.getCancels().get(0);
        if (existingLog != null && (existingLog.getPayment() == null
                || !Objects.equals(existingLog.getPayment().getId(), payment.getId())
                || !Objects.equals(existingLog.getPaymentKey(), paymentRequest.getPaymentKey())
                || !Objects.equals(existingLog.getOrderNumber(), order.getOrderNumber())
                || !Objects.equals(existingLog.getCancelTransactionKey(), cancel.getTransactionKey())
                || !Objects.equals(existingLog.getCancelAmount(), cancel.getCancelAmount())
                || !"CANCELED".equals(existingLog.getTossStatus())
                || !"DONE".equals(existingLog.getCancelStatus()))) {
            throw new PaymentException("[취소 복구 검증] 기존 PG 로그와 취소 거래 불일치: " + cancellationId);
        }
        boolean historyExists = historyRepository.existsByOutboundIdAndEventType(
                outbound.getId(), OutboundHistoryEventType.CANCEL_COMPLETED);

        // 조회 중 다른 실행이 완료했다면 일관된 완료 상태와 거래인지 확인한다.
        if (cancellation.getCancelStatus() == OrderCancelStatus.CANCELED) {
            if (order.getStatus() != OrderStatus.CANCELED || payment.getStatus() != PaymentStatus.CANCELED
                    || refund.getRefundStatus() != RefundStatus.COMPLETED
                    || outbound.getOutBoundStatus() != OutboundStatus.CANCELED
                    || !historyExists || existingLog == null
                    || !Objects.equals(refund.getTransactionKey(), cancel.getTransactionKey())
                    || !Objects.equals(refund.getRefundedAmount(), cancel.getCancelAmount())) {
                throw new OrderCancellationException("[취소 복구 검증] 완료 상태 또는 이력 불일치: " + cancellationId);
            }
            log.info("[취소 복구 중복 요청] 취소 ID: {}", cancellationId);
            return;
        }

        boolean matchingFailure =
                (cancellation.getCancelStatus() == OrderCancelStatus.PAYMENT_CANCEL_FAILED
                        && refund.getRefundStatus() == RefundStatus.FAILED)
                || (cancellation.getCancelStatus() == OrderCancelStatus.PAYMENT_CANCEL_FAILED_CHECK
                        && refund.getRefundStatus() == RefundStatus.FAILED_CHECK);
        if (!matchingFailure || payment.getStatus() != PaymentStatus.CANCEL_FAILED
                || order.getStatus() != OrderStatus.CANCEL_REQUEST || historyExists
                || outbound.getOutBoundStatus() == null
                || !Set.of(OutboundStatus.PENDING, OutboundStatus.ON_HOLD, OutboundStatus.PICKING,
                        OutboundStatus.PICKED, OutboundStatus.PACKING, OutboundStatus.PACKED)
                        .contains(outbound.getOutBoundStatus())) {
            throw new OrderCancellationException("[취소 복구 검증] 재처리 상태 또는 완료 이력 불일치: " + cancellationId);
        }

        // 외부 호출 없이 상태 및 두 이력을 한 트랜잭션에서 반영한다.
        OutboundStatus previousStatus = outbound.getOutBoundStatus();
        refund.retryUpdateStatusToComplete(paymentRequest);
        payment.updateCancelStatus();
        order.updateStatusToCancel();
        outbound.updateStatusToCancelled();
        cancellation.updateCancelStatusCanceled();
        if (existingLog == null) {
            paymentLogRepository.save(TossPaymentLog.createStatusToCancelSuccessInRetry(
                    cancellationId, payment, paymentRequest));
        }
        historyRepository.save(OutboundHistory.createForCancellationRecovered(
                outbound, previousStatus, cancellation.getCancelReason()));
        log.info("[취소 복구 완료] 취소 ID: {}, 주문 ID: {}, 결제 ID: {}, 환불 금액: {}",
                cancellationId, order.getId(), payment.getId(), cancel.getCancelAmount());
    }

    private void validateRecoveredPayment(TossPaymentRequestHistoryResponseDto response,
                                          Payment payment, Refund refund, Orders order) {
        if (response == null || payment.getPaymentKey() == null || payment.getPaymentKey().isBlank()
                || order.getOrderNumber() == null || payment.getAmount() == null
                || !Objects.equals(payment.getPaymentKey(), response.getPaymentKey())
                || !Objects.equals(order.getOrderNumber(), response.getOrderId())
                || !Objects.equals(payment.getAmount(), response.getTotalAmount())
                || !Objects.equals(payment.getAmount(), refund.getRequestedAmount())
                || !"CANCELED".equals(response.getStatus())
                || !Integer.valueOf(0).equals(response.getBalanceAmount())
                || response.getCancels() == null || response.getCancels().size() != 1
                || response.getCancels().get(0) == null) {
            throw new PaymentException("[취소 복구 PG 검증] 대상 결제·금액·전액 취소 상태 불일치. 결제 ID: " + payment.getId());
        }
        var cancel = response.getCancels().get(0);
        if (!"DONE".equals(cancel.getCancelStatus())
                || !Objects.equals(payment.getAmount(), cancel.getCancelAmount())
                || cancel.getCanceledAt() == null || cancel.getTransactionKey() == null
                || cancel.getTransactionKey().isBlank()
                || !Objects.equals(cancel.getTransactionKey(), response.getLastTransactionKey())) {
            throw new PaymentException("[취소 복구 PG 검증] 취소 거래 상태·금액·식별자·시각 불일치. 결제 ID: " + payment.getId());
        }
    }

    // 취소에 대한 내역이 없는 경우에는 TossAPI 취소 요청을 다시 보낼지 스케줄러 쪽에서 처리 될 수 있도록 상태 값을 변경할지
    public void cancelRetry(Long cancellationId, OrderCancellationRetryRecord data) {
    }
}