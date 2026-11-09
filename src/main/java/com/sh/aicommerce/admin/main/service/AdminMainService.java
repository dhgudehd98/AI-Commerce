package com.sh.aicommerce.admin.main.service;

import com.sh.aicommerce.admin.main.dto.response.AdminDashboardResponseDto;
import com.sh.aicommerce.admin.main.repository.AdminDashboardRepository;
import com.sh.aicommerce.common.exception.order.OrderException;
import com.sh.aicommerce.entity.PaymentCompensation;
import com.sh.aicommerce.enums.paymentLedger.PaymentLedgerType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class AdminMainService {
    private static final ZoneId OPERATIONS_ZONE = ZoneId.of("Asia/Seoul");
    private static final int DEFAULT_LOOKBACK_DAYS = 6; // 오늘을 포함한 최근 7일
    private static final Duration PENDING_ATTENTION_AFTER = Duration.ofMinutes(10);
    private static final Duration PROCESSING_ATTENTION_AFTER = Duration.ofMinutes(30);
    private final AdminDashboardRepository dashboardRepository;

    @Transactional(readOnly = true)
    public AdminDashboardResponseDto getStats(LocalDate from, LocalDate to) {
        OffsetDateTime asOf = OffsetDateTime.now(OPERATIONS_ZONE);
        LocalDate end = to == null ? asOf.toLocalDate() : to;
        if (end.equals(LocalDate.MAX))
            throw new OrderException("조회 종료일이 유효하지 않습니다.");

        LocalDate start = from == null ? end.minusDays(DEFAULT_LOOKBACK_DAYS) : from;

        if (start.isAfter(end))
            throw new OrderException("시작일은 종료일 이후일 수 없습니다.");

        // 선택한 종료일까지 포함하되 쿼리는 다음 날 00:00 미만으로 조회
        OffsetDateTime startAt = start.atStartOfDay(OPERATIONS_ZONE).toOffsetDateTime();
        OffsetDateTime until = end.plusDays(1).atStartOfDay(OPERATIONS_ZONE).toOffsetDateTime();
        long approvalAmount = 0, approvalCount = 0, cancellationAmount = 0, cancellationCount = 0;

        for (AdminDashboardRepository.LedgerTotals total : dashboardRepository.aggregateLedger(startAt, until)) {

            if (total.getType() == PaymentLedgerType.APPROVAL) {
                approvalAmount = total.getTotalAmount();
                approvalCount = total.getTransactionCount();
            } else if (total.getType() == PaymentLedgerType.CANCELLATION) {
                cancellationAmount = total.getTotalAmount();
                cancellationCount = total.getTransactionCount();
            }

        }

        // 업무 엔티티의 LocalDateTime은 기존 KST 기록 기준으로 비교
        LocalDateTime now = asOf.toLocalDateTime();
        return new AdminDashboardResponseDto(start, end, asOf,
                approvalAmount, approvalCount, cancellationAmount, cancellationCount,
                dashboardRepository.countAttentionOrders(now.minus(PENDING_ATTENTION_AFTER), now.minus(PROCESSING_ATTENTION_AFTER)),
                dashboardRepository.countPendingCompensations(),
                dashboardRepository.countUnconfirmedCancellations(),
                dashboardRepository.countFollowUpPendingCompensations(),
                dashboardRepository.countRetryExhaustedOrders(PaymentCompensation.MAX_RETRY_COUNT));
    }
}
