package com.sh.aicommerce.admin.batch.service;

import com.sh.aicommerce.admin.batch.dto.response.AdminCollectionRunResponseDto;
import com.sh.aicommerce.admin.batch.dto.response.AdminReconciliationRunResponseDto;
import com.sh.aicommerce.admin.batch.repository.AdminBatchRepository;
import com.sh.aicommerce.common.exception.payment.PaymentException;
import com.sh.aicommerce.entity.PgTransactionCollectionRun;
import com.sh.aicommerce.entity.PgTransactionReconciliationRun;
import com.sh.aicommerce.admin.batch.dto.response.AdminReconciliationResultResponseDto;
import com.sh.aicommerce.entity.PgTransactionReconciliationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.sh.aicommerce.admin.batch.dto.response.AdminMismatchResponseDto;
import com.sh.aicommerce.admin.batch.dto.response.AdminMismatchIssueResponseDto;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminBatchService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> STATUSES = Set.of("RUNNING", "COMPLETED", "FAILED");
    private final AdminBatchRepository repository;

    public Page<AdminCollectionRunResponseDto> collections(int page, int size, LocalDate from, LocalDate to, String status) {
        PageRequest request = validate(page, size, from, to, status);
        return repository.findCollections(from, to, status == null || status.isBlank()
                ? null : PgTransactionCollectionRun.Status.valueOf(status), request);
    }

    public Page<AdminReconciliationRunResponseDto> reconciliations(int page, int size, LocalDate from, LocalDate to, String status) {
        PageRequest request = validate(page, size, from, to, status);
        return repository.findReconciliations(from, to, status == null || status.isBlank()
                ? null : PgTransactionReconciliationRun.Status.valueOf(status), request);
    }

    public Page<AdminMismatchResponseDto> mismatches(Long runId, int page, int size) {
        PageRequest request = validate(page, size, null, null, null);
        if (runId == null || runId <= 0) {
            throw new PaymentException("대사 실행 ID가 유효하지 않습니다.");
        }
        if (repository.countRunById(runId) == 0) {
            throw new PaymentException("해당 대사 실행이 존재하지 않습니다.");
        }
        Page<AdminMismatchResponseDto> results = repository.findMismatches(runId, request);
        if (!results.isEmpty()) {
            List<Long> ids = results.getContent().stream().map(AdminMismatchResponseDto::getId).toList();
            Map<Long, List<AdminMismatchIssueResponseDto>> grouped = repository.findIssuesByResultIds(ids)
                    .stream().collect(Collectors.groupingBy(AdminMismatchIssueResponseDto::getResultId));
            results.forEach(result -> result.attachIssues(grouped.getOrDefault(result.getId(), List.of())));
        }
        return results;
    }

    public Page<AdminReconciliationResultResponseDto> results(
            Long runId, int page, int size, String status) {
        PageRequest request = validate(page, size, null, null, null);
        if (runId == null || runId <= 0 || repository.countRunById(runId) == 0) {
            throw new PaymentException("해당 대사 실행이 존재하지 않습니다.");
        }
        PgTransactionReconciliationResult.Status filter = null;
        if (status != null && !status.isBlank()) {
            try {
                filter = PgTransactionReconciliationResult.Status.valueOf(status);
            } catch (IllegalArgumentException e) {
                throw new PaymentException("결과 상태는 MATCHED, MISMATCHED, EXCLUDED 중 하나여야 합니다.");
            }
        }
        var results = repository.findResults(runId, filter, request);
        if (!results.isEmpty()) {
            var ids = results.getContent().stream().map(r -> r.getId()).toList();
            var grouped = repository.findIssuesByResultIds(ids).stream()
                    .collect(Collectors.groupingBy(AdminMismatchIssueResponseDto::getResultId));
            results.forEach(r -> r.attachIssues(grouped.getOrDefault(r.getId(), List.of())));
        }
        return results;
    }

    private PageRequest validate(int page, int size, LocalDate from, LocalDate to, String status) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new PaymentException("페이지는 0 이상, 조회 건수는 1~" + MAX_PAGE_SIZE + " 사이여야 합니다.");
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new PaymentException("시작일은 종료일 이후일 수 없습니다.");
        }
        if (status != null && !status.isBlank() && !STATUSES.contains(status)) {
            throw new PaymentException("배치 상태는 RUNNING, COMPLETED, FAILED 중 하나여야 합니다.");
        }
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
    }
}
