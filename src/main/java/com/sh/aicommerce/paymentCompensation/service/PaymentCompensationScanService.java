package com.sh.aicommerce.paymentCompensation.service;

import com.sh.aicommerce.common.exception.paymentCompensation.PaymentCompensationException;
import com.sh.aicommerce.payment.repository.PaymentRepository;
import com.sh.aicommerce.paymentCompensation.record.PaymentCompensationScanRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentCompensationScanService {
    private static final String PAYMENT_LOCK_PREFIX = "lock:payment:orderNumber:";
    private static final Duration PAYMENT_LOCK_TTL = Duration.ofSeconds(60);
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) end return 0", Long.class);

    private final PaymentRepository paymentRepository;
    private final PaymentCompensationTxService txService;
    private final StringRedisTemplate paymentLockTemplate;

    @Value("${payment.compensation.processing.stale-after:30m}")
    private Duration staleAfter;
    @Value("${payment.compensation.processing.batch-size:100}")
    private int batchSize;

    public void collectMissingCompensations() {
        if (staleAfter.isNegative() || staleAfter.isZero() || batchSize <= 0) {
            throw new PaymentCompensationException("보상 누락 수집 시간과 배치 크기는 양수여야 합니다.");
        }
        LocalDateTime cutoff = LocalDateTime.now().minus(staleAfter);
        List<PaymentCompensationScanRecord> targets = paymentRepository.findMissingProcessingCompensations(
                cutoff, PageRequest.of(0, batchSize));
        log.info("[PAYMENT] stage=COMPENSATION_SCAN event=CANDIDATES paymentStatus=PROCESSING count={}", targets.size());
        for (PaymentCompensationScanRecord target : targets) {
            try {
                prepareWithLock(target, cutoff);
            } catch (RuntimeException e) {
                // 한 건의 저장·락 오류가 나머지 결제의 누락 수집을 막지 않도록 합니다.
                log.error("[PAYMENT] stage=COMPENSATION_SCAN event=FAILED paymentId={} orderNumber={} errorType={}",
                        target.paymentId(), target.orderNumber(), e.getClass().getSimpleName());
            }
        }
    }

    // 해당 결제 내역 락 설정
    private void prepareWithLock(PaymentCompensationScanRecord target, LocalDateTime cutoff) {
        String key = PAYMENT_LOCK_PREFIX + target.orderNumber();
        String token = UUID.randomUUID().toString();
        if (!Boolean.TRUE.equals(paymentLockTemplate.opsForValue().setIfAbsent(key, token, PAYMENT_LOCK_TTL))) {
            log.info("[PAYMENT] stage=COMPENSATION_SCAN event=LOCK_BUSY paymentId={}", target.paymentId());
            return;
        }
        try {
            txService.prepareProcessingCompensation(target.paymentId(), target.orderNumber(), cutoff);
        } finally {
            try {
                // 결제 내역 락 해제
                paymentLockTemplate.execute(UNLOCK_SCRIPT, List.of(key), token);
            } catch (RuntimeException e) {
                log.error("[PAYMENT] stage=COMPENSATION_SCAN_UNLOCK event=FAILED paymentId={} errorType={}",
                        target.paymentId(), e.getClass().getSimpleName());
            }
        }
    }
}
