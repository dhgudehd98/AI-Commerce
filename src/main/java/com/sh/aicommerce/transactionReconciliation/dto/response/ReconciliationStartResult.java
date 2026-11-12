package com.sh.aicommerce.transactionReconciliation.dto.response;

/** 대사 시작 후 Listener에 전달하는 실행 연결 정보입니다. */
public record ReconciliationStartResult(
        Long reconciliationRunId, // 이번 대사 실행 ID
        Long collectionRunId      // 비교 대상으로 선택한 수집 실행 ID
) {
}
