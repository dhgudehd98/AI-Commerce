package com.sh.aicommerce.admin.Order.dto.response;

import com.sh.aicommerce.entity.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

@Getter
@NoArgsConstructor
public class PaymentInfoDto {
    private Long paymentId; // 결제 ID
    private String status; // 현재 결제 상태 코드
    private String statusLabel; // 결제 상태 표시명
    private Integer amount; // 원래 결제 요청 금액
    private String paymentMethod; // 결제 방법 코드
    private String paymentMethodLabel; // 결제 방법 표시명
    private String paymentKey; // PG 결제 식별자, 승인 전에는 null 가능
    private boolean compensationRequired; // 보상 대상 여부
    private String failReason; // 내부에 저장된 실패 사유
    private LocalDateTime createdAt; // 결제 준비 생성 시각
    private LocalDateTime updatedAt; // 마지막 갱신 시각
    private OffsetDateTime paidAt; // 승인 원장 occurredAt: 실제 PG 승인 시각, 원장이 없으면 null

    public PaymentInfoDto(Payment payment, PaymentLedger approvalLedger) {
        this.paymentId = payment.getId();
        this.status = payment.getStatus().name();
        this.statusLabel = payment.getStatus().getStatus();
        this.amount = payment.getAmount();
        this.paymentMethod = payment.getPaymentMethod().name();
        this.paymentMethodLabel = payment.getPaymentMethodLabel();
        this.paymentKey = payment.getPaymentKey();
        this.compensationRequired = payment.isCompensationRequired();
        this.failReason = payment.getFailReason();
        this.createdAt = payment.getCreatedAt();
        this.updatedAt = payment.getUpdatedAt();
        this.paidAt = approvalLedger == null ? null : approvalLedger.getOccurredAt();
    }
}
