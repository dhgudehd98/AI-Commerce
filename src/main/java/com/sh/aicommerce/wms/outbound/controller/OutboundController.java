package com.sh.aicommerce.wms.outbound.controller;


import com.sh.aicommerce.wms.outbound.dto.request.OutboundStatusRequestDto;
import com.sh.aicommerce.wms.outbound.dto.response.OutboundStatusResponseDto;
import com.sh.aicommerce.wms.outbound.dto.response.OutboundSummaryResponseDto;
import com.sh.aicommerce.wms.outbound.service.OutboundService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
//@RestController
@Controller
@RequestMapping("/api/admin/outbounds")
public class OutboundController {

    private final OutboundService outboundService;
    @GetMapping("")
    public String getOutboundList(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Model model
    ) {
        Page<OutboundSummaryResponseDto> outboundList = outboundService.getOutboundList(page, size);
        model.addAttribute("outboundList", outboundList);

        return "outbound/outboundList";
    }

    @GetMapping("{outboundId}")
    @ResponseBody
    public ResponseEntity<?> getOutboundDetail(
            @PathVariable Long outboundId
    ) {
        return ResponseEntity.ok(outboundService.getOutboundDetail(outboundId));
    }

    @PostMapping("{outboundId}/status")
    public ResponseEntity<?> chatOutboundStatus(
            @PathVariable Long outboundId,
            @Valid @RequestBody OutboundStatusRequestDto requestDto
//            Authentication authentication
    ) {
        Long memberId = 1L; // 관리자 ID -> 지금은 하드코딩으로 설정
        return ResponseEntity.ok(outboundService.changeOutboundStatus(memberId, requestDto.getStatus(), outboundId));

    }


}