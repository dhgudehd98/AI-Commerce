package com.sh.aicommerce.wms.inBound.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.jmx.export.annotation.ManagedNotifications;

import java.util.List;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class ProductVariantInboundReqDto {
    @NotNull
    private Long productVariantId;

    @NotEmpty
    private List<@Valid ProductOptionInboundReqDto> options;
}