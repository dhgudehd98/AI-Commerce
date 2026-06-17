package com.sh.aicommerce.wms.inBound.controller;

import com.sh.aicommerce.wms.inBound.dto.InboundRequestDto;
import com.sh.aicommerce.wms.inBound.service.InboundService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/inBound")
@Slf4j
public class InBoundController {

    private final InboundService inboundService;

    // 상품 입고
    @PostMapping("")
    public ResponseEntity<?> inBoundProduct(@RequestBody @Valid InboundRequestDto inboundRequestDto) {
        return inboundService.inBoundProduct(inboundRequestDto);
    }
}