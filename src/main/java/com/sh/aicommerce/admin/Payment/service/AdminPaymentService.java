package com.sh.aicommerce.admin.Payment.service;

import com.sh.aicommerce.admin.Payment.dto.response.AdminPaymentListResponseDto;
import com.sh.aicommerce.payment.repository.PaymentRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminPaymentService {

    private final PaymentRepository paymentRepository;

    @Transactional(readOnly = true)
    public Page<AdminPaymentListResponseDto> paymentList(int page, int size) {
        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "id")
        );

        return paymentRepository.findPaymentAll(pageable);
    }
}