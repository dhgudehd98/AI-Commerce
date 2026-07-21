package com.sh.aicommerce.card.service;


import com.sh.aicommerce.auth.repository.AuthRepository;
import com.sh.aicommerce.card.dto.SavedCardResponseDto;
import com.sh.aicommerce.card.repository.CardRepository;
import com.sh.aicommerce.common.exception.card.CardException;
import com.sh.aicommerce.common.exception.member.MemberException;
import com.sh.aicommerce.entity.Card;
import com.sh.aicommerce.entity.Member;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class CardService {
    private final CardRepository cardRepository;
    private final AuthRepository authRepository;

    public SavedCardResponseDto getCardInPayment(Long memberId) {
        Member member = authRepository.findById(memberId).orElseThrow(() -> new MemberException("존재하지 않는 회원정보입니다."));

        return cardRepository.findByMemberIdAndActiveTrueAndDefaultCardTrue(memberId)
                .map(card -> new SavedCardResponseDto(card))
                .orElse(null);
    }

    public List<SavedCardResponseDto> getCardListInPayment(Long memberId) {
        Member member = authRepository.findById(memberId).orElseThrow(() -> new MemberException("존재하지 않는 회원정보입니다."));

        List<Card> cards = cardRepository.findByMemberIdAndActiveTrue(memberId);

        log.info("[카드 리스트 조회] 등록된 카드 수 : {}", cards.size());

        return cards.stream()
                .map(card -> new SavedCardResponseDto(card))
                .toList();
    }
}