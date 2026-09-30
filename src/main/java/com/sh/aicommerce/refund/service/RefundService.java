package com.sh.aicommerce.refund.service;


import com.sh.aicommerce.common.exception.refund.RefundException;
import com.sh.aicommerce.entity.OrderCancellation;
import com.sh.aicommerce.entity.Payment;
import com.sh.aicommerce.entity.Refund;
import com.sh.aicommerce.refund.repository.RefundRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefundService {

    private final RefundRepository refundRepository;
    public Refund createRefund(OrderCancellation cancellation, Payment payment) {
        Refund existsRefund = refundRepository.
                findByOrderCancellationId(cancellation.getId())
                .orElse(null);

        if (existsRefund != null) {
            throw new RefundException(
                    "기존 환불 건이 존재합니다. refundId: " + existsRefund.getId()
                            + ", status: " + existsRefund.getRefundStatus()
                            + ". 상태 확인 후 관리자 재처리 경로를 사용해주세요."
            );
        }

        Refund refund = Refund.createRefund(cancellation, payment);
        return refundRepository.save(refund);
    }


    public Refund findRefundByCancellationIdWithLock(Long cancellationId) {
        return refundRepository.findByOrderCancellationIdWithLock(cancellationId)
                .orElseThrow(() -> new RefundException(
                        "환불 정보가 존재하지 않습니다. 주문 취소 ID: "
                                + cancellationId
                ));
    }
}