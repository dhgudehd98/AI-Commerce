package com.sh.aicommerce.toss.service;


import com.sh.aicommerce.auth.repository.AuthRepository;
import com.sh.aicommerce.card.repository.CardRepository;
import com.sh.aicommerce.common.exception.card.CardException;
import com.sh.aicommerce.common.exception.member.MemberException;
import com.sh.aicommerce.common.exception.payment.PaymentException;
import com.sh.aicommerce.entity.*;
import com.sh.aicommerce.payment.repository.PaymentRepository;
import com.sh.aicommerce.toss.dto.request.TossPaymentRequestDto;
import com.sh.aicommerce.toss.dto.response.TossPaymentBillingResponseDto;
import com.sh.aicommerce.toss.dto.response.TossPaymentSuccessResponseDto;
import com.sh.aicommerce.toss.repository.TossPaymentLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TossTransactionService {

    private final PaymentRepository paymentRepository;
    private final TossPaymentLogRepository logRepository;
    private final CardRepository cardRepository;
    private final AuthRepository authRepository;


    @Transactional
    public void validatePayment(TossPaymentRequestDto paymentDto) {
    }

    @Transactional(readOnly = true)
    public void validateCard(TossPaymentBillingResponseDto response) {

        if (cardRepository.existsByBillingKey(response.getBillingKey())) {
            throw new CardException("이미 등록된 카드 입니다.");
        }
    }

    @Transactional(readOnly = true)
    public void validateMemberByCustomerKey(String customerKey) {

        if (!authRepository.existsByCustomerKey(customerKey)) {
            throw new MemberException("존재하지 않는 회원입니다. 로그인을 다시해주세요.");
        }
    }

    @Transactional
    public void saveCard(String customerKey, TossPaymentBillingResponseDto response) {
        Member member = authRepository.findByCustomerKey(customerKey).orElseThrow(() -> new MemberException("존재하지 않는 회원입니다."));
        Card card = new Card(member, response);

        cardRepository.save(card);
        log.info("[토스페이먼츠 자동결제(Billing Key)] 카드 DB 저장 완료 : {}", card.getBillingKey());
    }


}