package com.sh.aicommerce.payment.service;

import com.sh.aicommerce.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentTxService {
    private final PaymentRepository paymentRepository;
}