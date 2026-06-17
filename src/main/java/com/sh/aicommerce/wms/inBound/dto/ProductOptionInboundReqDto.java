package com.sh.aicommerce.wms.inBound.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;



@Getter
@AllArgsConstructor
@NoArgsConstructor
public class ProductOptionInboundReqDto {

    @NotNull
    private Long productOptionId;

    @NotNull
    @Positive(message = "입고 수량은 양수에 대한 값만 입력할 수 있습니다.")
    private Integer inboundCount;


    @Positive(message = "양수 또는 0에 대한 값만 입력할 수 있습니다.")// 최소 유지수량에 대한 부분을 0으로 설정할 수도 있고 양수로 설정할 수도 있고
    private Integer safetyQuantity; // 최소 유지 수량
}