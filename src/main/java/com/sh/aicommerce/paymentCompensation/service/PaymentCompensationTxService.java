package com.sh.aicommerce.paymentCompensation.service;

import com.sh.aicommerce.common.exception.payment.PaymentException;
import com.sh.aicommerce.entity.Payment;
import com.sh.aicommerce.entity.PaymentAttempt;
import com.sh.aicommerce.entity.PaymentCompensation;
import com.sh.aicommerce.enums.payment.PaymentAttemptStatus;
import com.sh.aicommerce.enums.payment.PaymentStatus;
import com.sh.aicommerce.payment.dto.request.PaymentBillingCardRequestDto;
import com.sh.aicommerce.payment.repository.PaymentRepository;
import com.sh.aicommerce.paymentAttempt.repository.PaymentAttemptRepository;
import com.sh.aicommerce.paymentCompensation.repository.PaymentCompensationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentCompensationTxService {

    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository attemptRepository;
    private final PaymentCompensationRepository compensationRepository;

    /**
     * 보상 작업의 영속화만 수행합니다. 외부 PG 호출·예약 해제·출고 변경은 하지 않습니다.
     * 호출부에서 기존 주문번호 분산락을 획득한 뒤 호출해야 합니다.
     * 보상 실행은 별도 작업자가 담당하며 이 메서드에서 실행하지 않습니다.
     */
    @Transactional
    public Long prepareCompensation(PaymentBillingCardRequestDto request) {
        if (request == null || request.getPaymentId() == null || request.getAttemptId() == null
                || request.getOrderId() == null || request.getOrderNumber() == null) {
            throw new PaymentException("[결제/COMPENSATION_PREPARE] 결제·주문·승인 시도 식별자가 필요합니다.");
        }

        // 기존 결제 결과 반영과 동일하게 Payment → PaymentAttempt 순서로 잠급니다.
        Payment payment = paymentRepository.findPaymentByIdWithOrderForUpdate(
                        request.getPaymentId(), request.getOrderNumber())
                .orElseThrow(() -> new PaymentException("[결제/COMPENSATION_PREPARE] 결제 내역이 없습니다."));
        PaymentAttempt attempt = attemptRepository.findPaymentAttemptByAttemptIdForUpdate(request.getAttemptId())
                .orElseThrow(() -> new PaymentException("[결제/COMPENSATION_PREPARE] 승인 시도가 없습니다."));

        if (attempt.getPayment() == null || payment.getOrder() == null
                || !Objects.equals(attempt.getPayment().getId(), payment.getId())
                || !Objects.equals(payment.getOrder().getId(), request.getOrderId())
                || !Objects.equals(payment.getOrder().getOrderNumber(), request.getOrderNumber())
                || !Objects.equals(attempt.getOrderNumber(), request.getOrderNumber())
                || !Objects.equals(attempt.getRequestAmount(), payment.getAmount())
                || !Objects.equals(attempt.getRequestAmount(), request.getAmount())) {
            throw new PaymentException("[결제/COMPENSATION_PREPARE] 주문·결제·승인 시도의 연결 또는 금액이 일치하지 않습니다.");
        }

        // Payment 잠금으로 동일 결제의 동시 준비를 직렬화하고 고유 제약으로 중복 저장을 방어합니다.
        Optional<PaymentCompensation> existing = compensationRepository.findByPaymentAttemptId(attempt.getId());
        if (existing.isPresent()) return existing.get().getId();

        PaymentAttempt latest = attemptRepository.findFirstByPaymentIdOrderByIdDesc(payment.getId())
                .orElseThrow(() -> new PaymentException("[결제/COMPENSATION_PREPARE] 최신 승인 시도가 없습니다."));

        if (!Objects.equals(latest.getId(), attempt.getId())
                || !EnumSet.of(PaymentStatus.PROCESSING, PaymentStatus.UNKNOWN, PaymentStatus.PAID).contains(payment.getStatus())
                || !EnumSet.of(PaymentAttemptStatus.PROCESSING, PaymentAttemptStatus.UNKNOWN,
                        PaymentAttemptStatus.SUCCESS).contains(attempt.getStatus())) {
            throw new PaymentException("[결제/COMPENSATION_PREPARE] 보상 준비가 가능한 최신 승인 시도가 아닙니다.");
        }

        payment.requireCompensation();

        // 이미 확인된 승인 사실은 보존합니다. 예약 재고는 어떤 경우에도 여기서 해제하지 않습니다.
        if (payment.getStatus() != PaymentStatus.PAID && attempt.getStatus() != PaymentAttemptStatus.SUCCESS) {
            payment.updateStatusToCompensationPending();
            attempt.updateStatusToUnknown();
        }
        PaymentCompensation saved = compensationRepository.save(PaymentCompensation.create(attempt));
        log.warn("[PAYMENT] stage=COMPENSATION_PREPARE event=SAVED_PENDING_COMMIT paymentId={} attemptId={} compensationId={}",
                payment.getId(), attempt.getId(), saved.getId());
        return saved.getId();
    }
}
