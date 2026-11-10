package com.sh.aicommerce.toss.dto.transaction;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class TransactionResponseDto {
    @JsonProperty("mId")
    private String mId; // PG 상점 아이디
    private String transactionKey; // 승인·취소 각각의 거래 식별자
    private String paymentKey; // 승인·취소가 연결되는 결제 식별자
    private String orderId; // PG에 전달한 주문번호
    private String method; // 결제 수단: 카드, 간편결제 등
    private String customerKey; // 고객 식별자, 없으면 null
    private Boolean useEscrow; // 에스크로 사용 여부
    private String receiptUrl; // 영수증 URL
    private String status; // 해당 거래의 PG 상태
    private OffsetDateTime transactionAt; // 거래 발생 시각, UTC 오프셋 포함
    private String currency; // 통화 코드: KRW 등
    private Long amount; // 해당 거래 금액
}
