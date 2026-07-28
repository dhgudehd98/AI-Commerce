package com.sh.aicommerce.entity;

import com.sh.aicommerce.enums.payment.Bank;
import jakarta.persistence.*;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@NoArgsConstructor
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    private Member member;

    // 오픈뱅킹 또는 PG에서 발급받은 계좌 결제 토큰
    private String billingKey;

    @Enumerated(EnumType.STRING)
    private Bank bank;

    // 화면 표시용: ***-****-1234
    private String maskedAccountNumber;

    private String accountHolderName;

    private boolean defaultAccount;
    private boolean active;

    private LocalDateTime registeredAt;
    private LocalDateTime deletedAt;
}