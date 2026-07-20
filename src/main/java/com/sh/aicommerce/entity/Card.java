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
        //! 여기도 나중에 변경 어떻게 값을 맞춰야될지
        this.cardCompany = CardCompany.HYUNDAI;
//        this.cardCompany = CardCompany.valueOf(response.getCardCompany());
        this.maskedCardNumber = response.getCardNumber();
        this.cardType = CardType.CREDIT; //! 여기는 나중에 한국어로 변경해서 설정 CREDIT -> 신용, 체크 아래 코드로 변경
//        this.cardType = CardType.valueOf(response.getCard().getCardType());
        // 일단 등록을 하면 defaultCard, active에 대한 값들을 true로 설정
        this.defaultCard = true;
        this.active = true;
        this.registeredAt = LocalDateTime.now();
    }
}