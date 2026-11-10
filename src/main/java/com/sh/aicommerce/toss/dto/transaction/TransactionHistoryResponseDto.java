package com.sh.aicommerce.toss.dto.transaction;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class TransactionHistoryResponseDto {
    // PG가 반환한 최상위 JSON 배열의 개별 거래 목록
    private List<TransactionResponseDto> transactions;

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public TransactionHistoryResponseDto(List<TransactionResponseDto> transactions) {
        this.transactions = transactions;
    }
}
