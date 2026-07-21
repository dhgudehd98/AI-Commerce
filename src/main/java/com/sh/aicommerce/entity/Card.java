package com.sh.aicommerce.entity;

import com.sh.aicommerce.enums.payment.CardCompany;
import com.sh.aicommerce.enums.payment.CardType;
import com.sh.aicommerce.toss.dto.response.TossPaymentBillingResponseDto;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@NoArgsConstructor
@Getter
public class Card {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "card_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    // PG에서 발급받은 결제수단 식별 토큰
    @Column(nullable = false, unique = true)
    private String billingKey;

    @Enumerated(EnumType.STRING)
    private CardCompany cardCompany;

    // 화면 표시용: ****-****-****-1234
    private String maskedCardNumber;

    private String cardName;

    @Enumerated(EnumType.STRING)
    private CardType cardType; // 신용 / 체크 카드 구분

    private boolean defaultCard;
    private boolean active;

    private String ownerType;

    private LocalDateTime registeredAt;
    private LocalDateTime deletedAt;

    public Card(Member member, TossPaymentBillingResponseDto response) {
        this.member = member;
        this.billingKey = response.getBillingKey();
        this.cardCompany = CardCompany.fromCode(response.getCard().getIssuerCode());
        this.cardName = response.getCardCompany() + "카드";
        this.maskedCardNumber = response.getCardNumber();
        this.cardType = CardType.fromLabel(response.getCard().getCardType());
        this.defaultCard = true;
        this.active = true;
        this.registeredAt = LocalDateTime.now();
    }
}