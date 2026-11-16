package com.sh.aicommerce.admin.batch.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.sh.aicommerce.entity.PgTransactionReconciliationIssue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AdminMismatchIssueResponseDto {
    private final Long id;
    private final Long resultId; // 이 사유가 속한 거래별 검사 결과
    private final PgTransactionReconciliationIssue.Code code;
    private final String fieldName;
    private final JsonNode pgValue;
    private final JsonNode internalValue;
    private final String description;
}
