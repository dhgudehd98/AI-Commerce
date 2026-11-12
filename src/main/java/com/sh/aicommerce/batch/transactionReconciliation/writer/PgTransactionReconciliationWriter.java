package com.sh.aicommerce.batch.transactionReconciliation.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sh.aicommerce.common.exception.payment.PaymentException;
import com.sh.aicommerce.entity.*;
import com.sh.aicommerce.enums.paymentLedger.PaymentLedgerType;
import com.sh.aicommerce.payment.repository.PaymentRepository;
import com.sh.aicommerce.paymentCancellation.repository.PaymentCancellationRepository;
import com.sh.aicommerce.paymentLedger.repository.PaymentLedgerRepository;
import com.sh.aicommerce.transactionReconciliation.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RequiredArgsConstructor
@Slf4j
public class PgTransactionReconciliationWriter implements ItemWriter<PgTransaction> {
    private final Long collectionRunId;
    private final Long reconciliationRunId;
    private final PaymentRepository paymentRepository;
    private final PaymentLedgerRepository ledgerRepository;
    private final PgTransactionReconciliationRunRepository runRepository;
    private final PgTransactionReconciliationResultRepository resultRepository;
    private final PgTransactionReconciliationIssueRepository issueRepository;
    private final PaymentCancellationRepository cancellationRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void write(Chunk<? extends PgTransaction> chunk) {

        PgTransactionReconciliationRun run = runRepository.findById(reconciliationRunId)
                .orElseThrow(() -> new PaymentException("대사 실행이 없습니다."));

        if (!Objects.equals(run.getCollectionRun().getId(), collectionRunId)) {
            throw new PaymentException("대사 대상 수집 실행이 일치하지 않습니다.");
        }

        for (PgTransaction transaction : chunk.getItems()) {
            if (!Objects.equals(transaction.getCollectionRunId(), collectionRunId)) {
                throw new PaymentException("다른 수집 실행의 거래가 전달됐습니다.");
            }
            String action = transaction.getStatus();

            switch (action) {
                case "DONE" -> paymentCompare(transaction, run);
                case "CANCELED" -> cancellationCompare(transaction, run);
                default -> defaultCompare(transaction, run);
            }
        }
        // run은 영속 상태이므로 Chunk 커밋 시 Count도 함께 반영됩니다.
        log.info("[거래 대사 결과 저장 요청] runId={} chunkCount={} processedCount={}",
                reconciliationRunId, chunk.size(), run.getProcessedCount());
    }

    private void paymentCompare(PgTransaction transaction, PgTransactionReconciliationRun run) {
        Payment payment = paymentRepository.findPaymentByPaymentKey(transaction.getPaymentKey())
                .orElse(null);
        List<PgTransactionReconciliationIssue> issues = new ArrayList<>();
        PaymentLedger ledger = null;
        // PaymentKey에 일치하는 결제 정보가 일치 하지 않는 경우에는 결제 내역에 대한 필드값 검토를 할 이유가 없음.
        if (payment == null) {
            issues.add(
                    issue(
                            PgTransactionReconciliationIssue.Code.PAYMENT_NOT_FOUND,
                            "PAYMENT_KEY",
                            transaction.getPaymentKey(),
                            null,
                            "PG 거래의 PaymentKey와 일치하는 내부 결제 정보가 없습니다."
                    ));
        }
        /**
         * Payment 값이 존재하는 경우 안에 데이터 값 비교
         * - 주문번호(orderNumber)
         * - 결제금액(amount)
         * 결제 원장 - 승인 값 비교
         * - 결제 승인 원장이 존재하는지 확인
         * - transactionKey, paymentKey, amount, currency 가 동일한지 확인
         */
        else{
            // 주문번호 값 동일성검사
//            compare(issues, PgTransactionReconciliationIssue.Code.ORDER_NUMBER_MISMATCH, "orderNumber", transaction.getOrderId(), payment.getOrder().getOrderNumber());
            // 결제 금액 동일성 검사
            compare(issues, PgTransactionReconciliationIssue.Code.PAYMENT_AMOUNT_MISMATCH, "payment.amount", transaction.getAmount(), toLong(payment.getAmount()));
            ledger = ledgerRepository.findByPaymentIdAndType(payment.getId(), PaymentLedgerType.APPROVAL)
                    .orElse(null);

            if(ledger == null)
                issues.add(issue(
                        PgTransactionReconciliationIssue.Code.APPROVAL_LEDGER_NOT_FOUND,
                        "ledger.type",
                        PaymentLedgerType.APPROVAL.name(),
                        null,
                        "결제 원장에 결제 승인에 대한 내역이 존재하지 않습니다."
                ));
            else{
                compare(issues, PgTransactionReconciliationIssue.Code.APPROVAL_LEDGER_PAYMENT_KEY_MISMATCH,
                        "ledger.paymentKey", transaction.getPaymentKey(), ledger.getPaymentKey());
                compare(issues, PgTransactionReconciliationIssue.Code.APPROVAL_LEDGER_TRANSACTION_KEY_MISMATCH,
                        "ledger.transactionKey", transaction.getTransactionKey(), ledger.getTransactionKey());
                compare(issues, PgTransactionReconciliationIssue.Code.APPROVAL_LEDGER_AMOUNT_MISMATCH,
                        "ledger.amount", transaction.getAmount(), toLong(ledger.getAmount()));
                compare(issues, PgTransactionReconciliationIssue.Code.APPROVAL_LEDGER_CURRENCY_MISMATCH,
                        "ledger.currency", transaction.getCurrency(), ledger.getCurrency());
            }
        }

        // 결제 원장의 기록된 결제 금액과 실제 PG 거래금액이 동일하지 않은 경우 거래 금액 차ㅏㅏ 값 대입
        Long amountDiff = ledger == null || ledger.getAmount() == null || transaction.getAmount() == null
                ? null : transaction.getAmount() - ledger.getAmount().longValue();

        PgTransactionReconciliationResult.Status resultStatus = issues.size() == 0 ?
                PgTransactionReconciliationResult.Status.MATCHED : PgTransactionReconciliationResult.Status.MISMATCHED;

        // 하나의 거래 당 거래 결과 값 설정
        PgTransactionReconciliationResult result =
                PgTransactionReconciliationResult.builder()
                        .reconciliationRun(run)
                        .pgTransaction(transaction)
                        .payment(payment)
                        .paymentLedger(ledger)
                        .status(resultStatus)
                        .transactionType(PgTransactionReconciliationResult.TransactionType.APPROVAL)
                        .pgSnapshot(pgSnapshot(transaction))
                        .internalSnapshot(internalSnapshot(payment, ledger))
                        .amountDiff(amountDiff)
                        .build();

        resultRepository.save(result);
        issues.forEach(issue -> issue.saveResult(result));
        issueRepository.saveAll(issues);
        run.recordResult(result.getStatus(), ledger != null);

    }

    private void cancellationCompare(PgTransaction transaction, PgTransactionReconciliationRun run) {
        Payment payment = paymentRepository.findPaymentByPaymentKey(transaction.getPaymentKey())
                .orElse(null);

        // 만약에 부분 취소 기능이 도입이 된 경우, PaymentCancellation 쪽에 paymentKey에 해당 하는 취소 내역이 여러개 존재하기 떄문에
        // 거래에서 TransactionKey에 저장된 값으로 찾기
        PaymentCancellation cancellation = cancellationRepository.findByTransactionKey(transaction.getTransactionKey())
                .orElse(null);
        List<PgTransactionReconciliationIssue> issues = new ArrayList<>();
        PaymentLedger approvalLedger = null;
        PaymentLedger cancelLedger = null;

        if (cancellation == null) {
            issues.add(
                    issue(
                            PgTransactionReconciliationIssue.Code.CANCELLATION_NOT_FOUND,
                            "TRANSACTION_KEY",
                            transaction.getTransactionKey(),
                            null,
                            "PG 거래의 TransactionKey와 일치하는 내부 결제 취소 정보가 없습니다."

                    )
            );
        }else{
            // PaymentCancellation의 paymentId에 대한 값이 일치하는지 확인
            if (payment != null) {
                compare(issues, PgTransactionReconciliationIssue.Code.CANCELLATION_PAYMENT_MISMATCH, "cancel.payment", payment.getId(), cancellation.getPayment().getId());
            }
            // PG 거래에 존재하는 취소 금액과 결제 취소 내역에 존재하는 결제 취소 금액이 동잃산지 확인
            compare(issues, PgTransactionReconciliationIssue.Code.CANCELLATION_AMOUNT_MISMATCH,
                    "cancel.amount",
                    transaction.getAmount(),
                    toLong(cancellation.getCancelAmount()));

            // PG 주문번호와 취소 내역의 주문번호가 동일한지 확인
//            compare(issues, PgTransactionReconciliationIssue.Code.CANCELLATION_ORDER_NUMBER_MISMATCH,
//                    "cancel.orderNumber",
//                    transaction.getOrderId(),
//                    cancellation.getOrderCancellation().getOrder().getOrderNumber());

            cancelLedger = ledgerRepository.findByTransactionKeyAndType(transaction.getTransactionKey(), PaymentLedgerType.CANCELLATION)
                    .orElse(null);

            if (cancelLedger == null) {
                issues.add(issue(
                        PgTransactionReconciliationIssue.Code.CANCELLATION_LEDGER_NOT_FOUND,
                        "ledger.type",
                        PaymentLedgerType.CANCELLATION.name(),
                        null,
                        "결제 원장에 결제 취소에 대한 내역이 존재하지 않습니다."
                ));
            }else{
                // 값 비교
                // 취소 원장이 PG 거래의 원본 Payment에 연결되어 있는지 확인
                if (payment != null) {
                    compare(issues, PgTransactionReconciliationIssue.Code.CANCELLATION_LEDGER_PAYMENT_MISMATCH,
                            "cancelLedger.paymentId", payment.getId(),
                            cancelLedger.getPayment() == null ? null : cancelLedger.getPayment().getId());
                }

                // 취소 원장이 이번 PaymentCancellation에 연결되어 있는지 확인
                compare(issues, PgTransactionReconciliationIssue.Code.CANCELLATION_LEDGER_CANCELLATION_MISMATCH,
                        "cancelLedger.paymentCancellationId", cancellation.getId(),
                        cancelLedger.getPaymentCancellation() == null
                                ? null : cancelLedger.getPaymentCancellation().getId());

                // transactionKey, cancelAmount, paymentKey, currency 값 비교
                compare(issues, PgTransactionReconciliationIssue.Code.CANCELLATION_LEDGER_PAYMENT_KEY_MISMATCH,
                        "ledger.paymentKey", transaction.getPaymentKey(), cancelLedger.getPaymentKey());
                compare(issues, PgTransactionReconciliationIssue.Code.CANCELLATION_LEDGER_TRANSACTION_KEY_MISMATCH,
                        "ledger.transactionKey", transaction.getTransactionKey(), cancelLedger.getTransactionKey());
                compare(issues, PgTransactionReconciliationIssue.Code.CANCELLATION_LEDGER_AMOUNT_MISMATCH,
                        "ledger.amount", transaction.getAmount(), toLong(cancelLedger.getAmount()));
                compare(issues, PgTransactionReconciliationIssue.Code.CANCELLATION_LEDGER_CURRENCY_MISMATCH,
                        "ledger.currency", transaction.getCurrency(), cancelLedger.getCurrency());
            }
        }

        // PaymentKey에 일치하는 결제 정보가 일치 하지 않는 경우에는 결제 내역에 대한 필드값 검토를 할 이유가 없음.
        if (payment == null) {
            issues.add(
                    issue(
                            PgTransactionReconciliationIssue.Code.PAYMENT_NOT_FOUND,
                            "PAYMENT_KEY",
                            transaction.getPaymentKey(),
                            null,
                            "PG 거래의 PaymentKey와 일치하는 내부 결제 정보가 없습니다."
                    ));
        }
        /**
         * Payment 값이 존재하는 경우 안에 데이터 값 비교
         * - 주문번호(orderNumber)
         * - 결제금액(amount)
         * 결제 원장 - 승인 값 비교
         * - 결제 승인 원장이 존재하는지 확인
         * - transactionKey, paymentKey, amount, currency 가 동일한지 확인
         */
        else{
            // 주문번호 값 동일성검사
//            compare(issues, PgTransactionReconciliationIssue.Code.ORDER_NUMBER_MISMATCH, "orderNumber", transaction.getOrderId(), payment.getOrder().getOrderNumber());
            // 결제 금액 동일성 검사
            compare(issues, PgTransactionReconciliationIssue.Code.PAYMENT_AMOUNT_MISMATCH, "payment.amount", transaction.getAmount(), toLong(payment.getAmount()));
            approvalLedger = ledgerRepository.findByPaymentIdAndType(payment.getId(), PaymentLedgerType.APPROVAL)
                    .orElse(null);

            if(approvalLedger == null)
                issues.add(issue(
                        PgTransactionReconciliationIssue.Code.APPROVAL_LEDGER_NOT_FOUND,
                        "ledger.type",
                        PaymentLedgerType.APPROVAL.name(),
                        null,
                        "결제 원장에 결제 승인에 대한 내역이 존재하지 않습니다."
                ));
            else{
                compare(issues, PgTransactionReconciliationIssue.Code.APPROVAL_LEDGER_PAYMENT_KEY_MISMATCH,
                        "ledger.paymentKey", transaction.getPaymentKey(), approvalLedger.getPaymentKey());
                compare(issues, PgTransactionReconciliationIssue.Code.APPROVAL_LEDGER_AMOUNT_MISMATCH,
                        "ledger.amount", transaction.getAmount(), toLong(approvalLedger.getAmount()));
                compare(issues, PgTransactionReconciliationIssue.Code.APPROVAL_LEDGER_CURRENCY_MISMATCH,
                        "ledger.currency", transaction.getCurrency(), approvalLedger.getCurrency());
            }
        }

        // 결제 원장의 기록된 결제 금액과 실제 PG 거래금액이 동일하지 않은 경우 거래 금액 차ㅏㅏ 값 대입
        Long amountDiff = cancelLedger == null || cancelLedger.getAmount() == null || transaction.getAmount() == null
                ? null : transaction.getAmount() - cancelLedger.getAmount().longValue();

        PgTransactionReconciliationResult.Status resultStatus = issues.size() == 0 ?
                PgTransactionReconciliationResult.Status.MATCHED : PgTransactionReconciliationResult.Status.MISMATCHED;

        // 하나의 거래 당 거래 결과 값 설정
        PgTransactionReconciliationResult result =
                PgTransactionReconciliationResult.builder()
                        .reconciliationRun(run)
                        .pgTransaction(transaction)
                        .payment(payment)
                        .paymentLedger(cancelLedger)
                        .status(resultStatus)
                        .transactionType(PgTransactionReconciliationResult.TransactionType.CANCELLATION)
                        .pgSnapshot(pgSnapshot(transaction))
                        .internalSnapshot(internalSnapshot(payment, cancelLedger))
                        .amountDiff(amountDiff)
                        .build();

        resultRepository.save(result);
        issues.forEach(issue -> issue.saveResult(result));
        issueRepository.saveAll(issues);
        run.recordResult(result.getStatus(), cancelLedger != null);

    }

    private void compare(
            List<PgTransactionReconciliationIssue> issues,
            PgTransactionReconciliationIssue.Code errorCode,
            String fieldName,
            Object pgValue,
            Object internalValue
    ) {
        if(!Objects.equals(pgValue, internalValue))
            issues.add(issue(errorCode, fieldName, pgValue, internalValue,
                    fieldName + " 값이 일치하지 않습니다. "));
    }

    private void defaultCompare(PgTransaction transaction, PgTransactionReconciliationRun run) {
        PgTransactionReconciliationResult result = PgTransactionReconciliationResult.builder()
                .reconciliationRun(run).pgTransaction(transaction)
                .transactionType(PgTransactionReconciliationResult.TransactionType.UNSUPPORTED)
                .status(PgTransactionReconciliationResult.Status.EXCLUDED)
                .exclusionCode("UNSUPPORTED_PG_STATUS")
                .exclusionReason("지원하지 않는 PG 거래 상태: " + transaction.getStatus())
                .pgSnapshot(pgSnapshot(transaction)).build();
        resultRepository.save(result);
        run.recordResult(result.getStatus(), false);
    }

    private PgTransactionReconciliationIssue issue(
            PgTransactionReconciliationIssue.Code errorCode,
            String fieldName,
            Object pgValue,
            Object internalValue,
            String description)
    {
        return PgTransactionReconciliationIssue.builder()
                .code(errorCode)
                .fieldName(fieldName)
                .pgValue(objectMapper.valueToTree(pgValue))
                .internalValue(objectMapper.valueToTree(internalValue))
                .description(description)
                .build();


    }

    private Long toLong(Integer value) {
        return value == null ? null : value.longValue();
    }

    private Map<String, Object> internalSnapshot(Payment payment, PaymentLedger ledger) {
        if (payment == null) return null;
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("paymentId", payment.getId());
        snapshot.put("paymentKey", payment.getPaymentKey());
        snapshot.put("paymentAmount", payment.getAmount());
        snapshot.put("orderNumber", payment.getOrder() == null ? null : payment.getOrder().getOrderNumber());
        if (ledger != null) {
            snapshot.put("ledgerId", ledger.getId());
            snapshot.put("ledgerType", ledger.getType().name());
            snapshot.put("ledgerPaymentKey", ledger.getPaymentKey());
            snapshot.put("transactionKey", ledger.getTransactionKey());
            snapshot.put("amount", ledger.getAmount());
            snapshot.put("currency", ledger.getCurrency());
            snapshot.put("occurredAt", ledger.getOccurredAt().toString());
        }
        return snapshot;
    }

    private Map<String, Object> pgSnapshot(PgTransaction transaction) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("mId", transaction.getMId());
        snapshot.put("paymentKey", transaction.getPaymentKey());
        snapshot.put("transactionKey", transaction.getTransactionKey());
        snapshot.put("orderNumber", transaction.getOrderId());
        snapshot.put("status", transaction.getStatus());
        snapshot.put("amount", transaction.getAmount());
        snapshot.put("currency", transaction.getCurrency());
        snapshot.put("transactionAt", transaction.getTransactionAt().toString());
        return snapshot;
    }


}
