package com.sh.aicommerce.wms.outbound.controller;


import com.sh.aicommerce.wms.outbound.dto.response.OutboundSummaryResponseDto;
import com.sh.aicommerce.wms.outbound.service.OutboundService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
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


}