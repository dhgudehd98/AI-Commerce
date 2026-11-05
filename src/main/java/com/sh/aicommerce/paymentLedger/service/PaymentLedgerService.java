package com.sh.aicommerce.paymentLedger.service;

import com.sh.aicommerce.common.exception.paymentLedger.PaymentLedgerException;
import com.sh.aicommerce.entity.PaymentLedger;
import com.sh.aicommerce.paymentLedger.repository.PaymentLedgerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PaymentLedgerService {
    private final PaymentLedgerRepository ledgerRepository;

    /** 호출부가 결제·취소를 잠근 결과 반영 트랜잭션에 참여합니다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void saveIfAbsent(PaymentLedger ledger) {
        PaymentLedger existing = ledgerRepository.findByPaymentIdAndType(
                ledger.getPayment().getId(), ledger.getType()).orElse(null);
        if (existing == null) {
            ledgerRepository.save(ledger);
            return;
        }
        Long existingCancellationId = existing.getPaymentCancellation() == null
                ? null : existing.getPaymentCancellation().getId();
        Long cancellationId = ledger.getPaymentCancellation() == null
                ? null : ledger.getPaymentCancellation().getId();
        if (!Objects.equals(existingCancellationId, cancellationId)
                || !Objects.equals(existing.getAmount(), ledger.getAmount())
                || !Objects.equals(existing.getCurrency(), ledger.getCurrency())
                || !Objects.equals(existing.getPaymentKey(), ledger.getPaymentKey())
                || !Objects.equals(existing.getTransactionKey(), ledger.getTransactionKey())
                || !existing.getOccurredAt().isEqual(ledger.getOccurredAt())) {
            throw new PaymentLedgerException("[결제/LEDGER] 기존 원장과 PG 거래 정보 또는 취소 연결이 일치하지 않습니다.");
        }
        // 같은 거래는 저장만 생략합니다. 호출부의 보상·복구 처리는 계속됩니다.
    }
}
