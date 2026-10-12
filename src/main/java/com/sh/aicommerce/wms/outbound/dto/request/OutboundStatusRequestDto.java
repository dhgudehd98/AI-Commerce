package com.sh.aicommerce.wms.outbound.dto.request;

import com.sh.aicommerce.wms.outbound.enums.OutboundStatus;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class OutboundStatusRequestDto {
    @NotNull
    OutboundStatus status;
}