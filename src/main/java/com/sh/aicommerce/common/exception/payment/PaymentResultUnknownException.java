package com.sh.aicommerce.common.exception.payment;

public class PaymentResultUnknownException extends PaymentException {
    public static final String USER_MESSAGE =
            "결제 결과를 확인하지 못했습니다. 이미 승인되었을 수 있으니 새 주문으로 다시 결제하지 마시고, 주문번호를 보관한 뒤 관리자에게 문의해주세요.";

    public PaymentResultUnknownException(Throwable cause) {
        super(USER_MESSAGE);
        initCause(cause);
    }
}
