package com.sh.aicommerce.payment.dto.request;

import com.sh.aicommerce.enums.payment.CardCompany;
import com.sh.aicommerce.enums.payment.GeneralPayment;
import com.sh.aicommerce.enums.payment.PaymentMethod;
import com.sh.aicommerce.order.dto.Receiver;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class PaymentRequestDto {

    // 받는 사람 정보
    private Receiver receiver;

    // 상품 정보
    private Long variantId;
    private Long optionId;

    // 쿠폰 정보
    private Long couponId;

    // 포인트
    private Integer usedPoint;
    // 결제 방법
    private PaymentMethod paymentMethod; // 결제 방식
    private Long savedAccountId; // 결제방식 > 계좌 간편 결제 > 선택한 계좌 ID
    private Long savedCardId; // 결제 방식 > 카드 간편 결제 > 선택한 카드 ID
    private GeneralPayment generalPayment; // 일반 결제 > 신용카드 , 네이버 페이 , 카카오페이
    private CardCompany cardCompany; // 일반 결제 > 신용카드 선택시
    private Integer installmentMonths; // 일반 결제 -> 신용카드 -> 할부 || 일시불

    // 주문정보
//    Integer productPrice; // 상품 가격
//    Integer deliveryPrice; // 배송비
//    Integer couponPrice; // 쿠폰 가격
//    Integer paymentTotalPrice; // 총 상품 가격


    @Override
    public String toString() {
        return "결제 요청 -> 상품 정보 {" +
                "receiver=" + receiver +
                ", variantId=" + variantId +
                ", optionId=" + optionId +
                ", usedPoint=" + usedPoint +
                ", paymentMethod=" + paymentMethod.name() +
                ", savedAccountId=" + savedAccountId +
                ", savedCardId=" + savedCardId +
                '}';
    }
}