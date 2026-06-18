package com.multi.finance.controller;

import com.multi.finance.dto.response.AccountantDashboardResponse;
import com.multi.finance.dto.response.CollectionHealthResponse;
import com.multi.finance.dto.response.OwnerDashboardResponse;
import com.multi.finance.dto.response.ShopDashboardResponse;
import com.multi.finance.dto.response.UpcomingChequeEntry;
import com.multi.finance.enums.BusinessType;
import com.multi.finance.service.impl.DashboardServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;


@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardServiceImpl dashboardService;

    @GetMapping("/accountant")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'MAIN_ACCOUNTANT')")
    public ResponseEntity<AccountantDashboardResponse> getAccountantDashboard() {
        return ResponseEntity.ok(dashboardService.getAccountantDashboard());
    }

    @GetMapping("/owner")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'MAIN_ACCOUNTANT')")
    public ResponseEntity<OwnerDashboardResponse> getOwnerDashboard(
            @RequestParam(required = false, defaultValue = "RAINCO") BusinessType business) {
        return ResponseEntity.ok(dashboardService.getOwnerDashboard(business));
    }

    @GetMapping("/shop")
    @PreAuthorize("hasAnyRole('ADMIN', 'SHOP_ACCOUNTANT')")
    public ResponseEntity<ShopDashboardResponse> getShopDashboard() {
        return ResponseEntity.ok(dashboardService.getShopDashboard());
    }

    @GetMapping("/collection-health")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'MAIN_ACCOUNTANT', 'ACCOUNTANT')")
    public ResponseEntity<CollectionHealthResponse> getCollectionHealth(
            @RequestParam(required = false, defaultValue = "RAINCO") BusinessType business) {
        return ResponseEntity.ok(dashboardService.getCollectionHealth(business));
    }

    /** Upcoming cheques for a custom date range — lets the user look further ahead than the default 30-day window. */
    @GetMapping("/upcoming-cheques")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'MAIN_ACCOUNTANT', 'ACCOUNTANT')")
    public ResponseEntity<List<UpcomingChequeEntry>> getUpcomingCheques(
            @RequestParam(required = false, defaultValue = "RAINCO") BusinessType business,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(dashboardService.getUpcomingCheques(business, from, to));
    }
}
