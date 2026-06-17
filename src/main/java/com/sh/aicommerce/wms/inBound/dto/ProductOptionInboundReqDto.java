package com.sh.aicommerce.wms.inBound.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class ProductOptionInboundReqDto {

    private Long productOptionId;
    private Integer inboundCount;
    private Integer safetyQuantity; // 최소 유지 수량
}