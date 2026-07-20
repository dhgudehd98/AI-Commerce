package com.sh.aicommerce.card.controller;

import com.sh.aicommerce.card.service.CardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/card")
public class CardController {

    private final CardService cardService;

    @GetMapping("")
    public ResponseEntity<?> getCardInPayment(
//            Authentication authentication
    ) {
        Long memberId = 1L;
        return ResponseEntity.ok(cardService.getCardInPayment(memberId));
    }

    @GetMapping("/list")
    public ResponseEntity<?> getCardListInPayment(
//            Authentication authentication
    ) {
        Long memberId = 1L;
        return ResponseEntity.ok(cardService.getCardListInPayment(memberId));
    }
}