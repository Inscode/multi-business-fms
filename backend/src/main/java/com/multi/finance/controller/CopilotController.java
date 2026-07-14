package com.multi.finance.controller;

import com.multi.finance.dto.response.*;
import com.multi.finance.service.impl.CopilotService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/copilot")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
public class CopilotController {

    private final CopilotService copilotService;

    /**
     * GET /api/copilot/bills/outstanding
     * Returns outstanding bills with optional filters.
     */
    @GetMapping("/bills/outstanding")
    public ResponseEntity<List<OutstandingBillDTO>> getOutstandingBills(
            @RequestParam(required = false) String business,
            @RequestParam(required = false) String area,
            @RequestParam(required = false) String tier,
            @RequestParam(required = false) Integer minDays,
            @RequestParam(required = false) String customer,
            @RequestParam(required = false) Boolean overdueOnly) {

        return ResponseEntity.ok(
                copilotService.getOutstandingBills(business, area, tier, minDays, customer, overdueOnly));
    }

    /**
     * GET /api/copilot/bills/aging
     * Returns per-business aging buckets for outstanding bills.
     */
    @GetMapping("/bills/aging")
    public ResponseEntity<Map<String, AgingBucketDTO>> getAgingReport(
            @RequestParam(required = false) String business) {

        return ResponseEntity.ok(copilotService.getAgingReport(business));
    }

    /**
     * GET /api/copilot/customers/profile?name=...
     * Returns full customer profile: info, unpaid bills, payment history, reminders.
     */
    @GetMapping("/customers/profile")
    public ResponseEntity<CustomerProfileDTO> getCustomerProfile(
            @RequestParam String name) {

        return ResponseEntity.ok(copilotService.getCustomerProfile(name));
    }

    /**
     * GET /api/copilot/bills/call-list
     * Returns today's prioritised call list: overdue bills + due reminders.
     */
    @GetMapping("/bills/call-list")
    public ResponseEntity<List<CallListItemDTO>> getCallList() {
        return ResponseEntity.ok(copilotService.getCallList());
    }
}
