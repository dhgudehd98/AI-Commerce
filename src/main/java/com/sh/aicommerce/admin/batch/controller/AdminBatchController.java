package com.sh.aicommerce.admin.batch.controller;

import com.sh.aicommerce.admin.batch.dto.response.AdminCollectionRunResponseDto;
import com.sh.aicommerce.admin.batch.dto.response.AdminMismatchResponseDto;
import com.sh.aicommerce.admin.batch.dto.response.AdminReconciliationRunResponseDto;
import com.sh.aicommerce.admin.batch.service.AdminBatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

@Controller
@RequiredArgsConstructor
@RequestMapping("/api/admin/batch")
public class AdminBatchController {
    private final AdminBatchService adminBatchService;

    @GetMapping("")
    public String batch() {
        return "admin/batch/batchList";
    }

    @GetMapping({"/list", "/reconciliation/list"})
    @ResponseBody
    public ResponseEntity<Page<AdminReconciliationRunResponseDto>> reconciliations(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(adminBatchService.reconciliations(page, size, from, to, status));
    }

    @GetMapping("/collection/list")
    @ResponseBody
    public ResponseEntity<Page<AdminCollectionRunResponseDto>> collections(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(adminBatchService.collections(page, size, from, to, status));
    }

    @GetMapping("/reconciliation/{runId}/mismatches")
    @ResponseBody
    public ResponseEntity<Page<AdminMismatchResponseDto>> mismatches(
            @PathVariable Long runId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(adminBatchService.mismatches(runId, page, size));
    }
}
