package com.sh.aicommerce.admin.Order.service;

import com.sh.aicommerce.admin.Order.dto.response.*;
import com.sh.aicommerce.common.exception.order.OrderException;
import com.sh.aicommerce.order.orderRepository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.sh.aicommerce.paymentCompensation.repository.PaymentCompensationRepository;
import com.sh.aicommerce.entity.PaymentLedger;
import com.sh.aicommerce.enums.paymentLedger.PaymentLedgerType;
import com.sh.aicommerce.paymentLedger.repository.PaymentLedgerRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminOrderService {

    private final OrderRepository orderRepository;
    private final PaymentCompensationRepository compensationRepository;
    private final PaymentLedgerRepository ledgerRepository;

    private static final int MAX_PAGE_SIZE = 100;

    @Transactional(readOnly = true)
    public Page<OrdersListResponseDto> ordersList(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new OrderException("페이지는 0 이상, 조회 건수는 1~" + MAX_PAGE_SIZE + " 사이여야 합니다.");
        }
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));

        return orderRepository.findAllForAdmin(pageable);
    }

    @Transactional(readOnly = true)
    public AdminOrderTimelineResponseDto getOrderTimeLine(Long orderId) {

        AdminOrderTimelineRecord record = orderRepository.findOrderTimeRecordByOrderId(orderId)
                .orElseThrow(() -> new OrderException("해당 주문 내역이 존재하지 않습니다."));

        CompensationInfoDto compensation = compensationRepository.findByOrderId(orderId)
                .map(CompensationInfoDto::new).orElse(null);

        PaymentLedger approvalLedger = null;
        PaymentLedger cancellationLedger = null;

        if (record.payment() != null) {
            Long paymentId = record.payment().getId();
            approvalLedger = ledgerRepository.findByPaymentIdAndType(paymentId, PaymentLedgerType.APPROVAL).orElse(null);
            cancellationLedger = ledgerRepository.findByPaymentIdAndType(paymentId, PaymentLedgerType.CANCELLATION).orElse(null);
        }

        CancellationInfoDto cancellation = record.orderCancellation() == null ? null
                : new CancellationInfoDto(record.orderCancellation(), record.paymentCancellation(), cancellationLedger);

        return new AdminOrderTimelineResponseDto(
                new OrderInfoDto(record.order()),
                record.payment() == null ? null : new PaymentInfoDto(record.payment(), approvalLedger),
                cancellation,
                compensation);
    }
}
