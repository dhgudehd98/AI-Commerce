package com.sh.aicommerce.wms.inBound.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;


import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Getter
public class InboundRequestDto {

    @NotNull
    private Long productId;

    @NotNull
    private Long warehouseId;

    @NotEmpty
    List<@Valid ProductOptionInboundReqDto> items;

}