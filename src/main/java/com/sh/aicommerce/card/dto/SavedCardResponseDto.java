package com.sh.aicommerce.card.dto;

import com.sh.aicommerce.entity.Card;
import jakarta.annotation.security.DenyAll;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SavedCardResponseDto {
    private Long cardId;
    private String cardName;
    private String maskedCardNumber;
    private String billingKey;

    public SavedCardResponseDto(Card card) {
        this.cardId = card.getId();
        this.cardName = card.getCardName();
        this.maskedCardNumber = card.getMaskedCardNumber();
        this.billingKey = card.getBillingKey();
    }

    @Override
    public String toString() {
        return "SavedCardResponseDto{" +
                "cardName='" + cardName + '\'' +
                ", maskedCardNumber='" + maskedCardNumber + '\'' +
                ", billingKey='" + billingKey + '\'' +
                '}';
    }
}