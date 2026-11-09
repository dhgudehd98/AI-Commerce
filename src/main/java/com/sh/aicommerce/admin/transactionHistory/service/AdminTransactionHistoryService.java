package com.sh.aicommerce.admin.transactionHistory.service;

import com.sh.aicommerce.toss.dto.transaction.TransactionHistoryResponseDto;
import com.sh.aicommerce.toss.service.TossPaymentClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminTransactionHistoryService {

    // 일단 API 요청으로만 설정
    private final TossPaymentClient client;

    public TransactionHistoryResponseDto getHistory(LocalDateTime from, LocalDateTime now) {

        return client.transactionHistory(from, now);
    }

    // PG 직접 조회용 임시 구현. DB 수집 도입 시 이 조회 경로를 교체합니다.
    public TransactionHistoryResponseDto getHistory(LocalDateTime from, LocalDateTime to,
                                                    String startingAfter, Integer limit) {
        return client.transactionHistory(from, to, startingAfter, limit);
    }
}
