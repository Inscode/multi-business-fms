package com.multi.finance.controller;

import com.multi.finance.dto.response.AccountantDashboardResponse;
import com.multi.finance.dto.response.OwnerDashboardResponse;
import com.multi.finance.enums.BusinessType;
import com.multi.finance.service.impl.DashboardServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardServiceImpl dashboardService;

    @GetMapping("/accountant")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<AccountantDashboardResponse> getAccountantDashboard() {
        return ResponseEntity.ok(dashboardService.getAccountantDashboard());
    }

    @GetMapping("/owner")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<OwnerDashboardResponse> getOwnerDashboard(
            @RequestParam(required = false, defaultValue = "RAINCO") BusinessType business) {
        return ResponseEntity.ok(dashboardService.getOwnerDashboard(business));
    }
}
