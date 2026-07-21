package com.sh.aicommerce.enums.payment;


public enum CardType {
    CREDIT("신용"),
    CHECK("체크");
    private String cardTypeLabel;

    CardType(String cardTypeLabel) {
        this.cardTypeLabel = cardTypeLabel;
    }

    public static CardType fromLabel(String cardTypeLabel) {
        for (CardType type : values()) {
            if(type.cardTypeLabel.equals(cardTypeLabel)) return type;
        }

        throw new IllegalArgumentException("지원하지 않는 카드 타입입니다: " +
                cardTypeLabel);
    }
}