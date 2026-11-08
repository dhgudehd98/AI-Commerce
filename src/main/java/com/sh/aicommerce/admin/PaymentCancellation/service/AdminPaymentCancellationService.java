package com.sh.aicommerce.admin.PaymentCancellation.service;

import com.sh.aicommerce.admin.PaymentCancellation.dto.response.AdminPaymentCancellationListResponseDto;
import com.sh.aicommerce.admin.PaymentCancellation.repository.AdminPaymentCancellationRepository;
import com.sh.aicommerce.common.exception.payment.PaymentException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminPaymentCancellationService {
    private static final int MAX_PAGE_SIZE = 100;
    private final AdminPaymentCancellationRepository repository;

    @Transactional(readOnly = true)
    public Page<AdminPaymentCancellationListResponseDto> cancellationList(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new PaymentException("페이지는 0 이상, 조회 건수는 1~" + MAX_PAGE_SIZE + " 사이여야 합니다.");
        }
        return repository.findAllForAdmin(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")));
    }
}
