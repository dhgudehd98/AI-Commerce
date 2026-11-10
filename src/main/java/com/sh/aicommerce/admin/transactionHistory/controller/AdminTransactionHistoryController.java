package com.sh.aicommerce.admin.transactionHistory.controller;

import com.sh.aicommerce.admin.transactionHistory.service.AdminTransactionHistoryService;
import com.sh.aicommerce.toss.dto.transaction.TransactionHistoryResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.LocalDateTime;

@Controller
@RequiredArgsConstructor
@RequestMapping("/api/admin/transaction/history")
public class AdminTransactionHistoryController {

    private final AdminTransactionHistoryService adminTransactionHistoryService;

    @GetMapping("")
    public String history() {
        return "admin/transaction/transaction-history";
    }

    // 여기서 데이터 조회 - 나중에는 DB에서 꺼내도록 해야될거 같은데 지금은 토스 API를 통해서 할 수 있도록
    @GetMapping("/list")
    @ResponseBody
    public ResponseEntity<TransactionHistoryResponseDto> getHistory(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) String startingAfter,
            @RequestParam(defaultValue = "100") Integer limit
    ) {
        return ResponseEntity.ok(adminTransactionHistoryService.getHistory(from, to, startingAfter, limit));
    }
}
