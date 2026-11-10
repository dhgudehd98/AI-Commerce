package com.sh.aicommerce.admin.Main.controller;

import com.sh.aicommerce.admin.Main.dto.response.AdminDashboardResponseDto;
import com.sh.aicommerce.admin.Main.service.AdminMainService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.LocalDate;

@Controller
@RequiredArgsConstructor
public class AdminMainController {
    private final AdminMainService adminMainService;

    @GetMapping({"/api/admin", "/api/admin/", "/api/admin/main"})
    public String main() {
        return "admin/main/dashboard";
    }

    @GetMapping({"/api/admin/stats", "/api/admin/main/stats"})
    @ResponseBody
    public ResponseEntity<AdminDashboardResponseDto> stats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(adminMainService.getStats(from, to));
    }
}
