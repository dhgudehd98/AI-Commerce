package com.sh.aicommerce.toss.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TossPaymentCancelResponseDto {
    @JsonProperty("mId")
    private String mId;
    private String version;
    private String lastTransactionKey;
    private String paymentKey;
    private String orderId;
    private String orderName;
    private String currency;
    private String method;
    private String status;
    private List<CancelResponseDto> cancels;
    private String secret;
    private String type;
    // 문자열 예시와 객체 형태의 응답 모두 수용한다.
    private JsonNode easyPay;
    private String country;
    private Failure failure;
    private Integer totalAmount;
    private Integer balanceAmount;
    private Integer suppliedAmount;
    private Integer vat;
    private JsonNode metadata;
    private Integer taxFreeAmount;
    private Integer taxExemptionAmount;

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Failure {
        private String code;
        private String message;
    }
}
