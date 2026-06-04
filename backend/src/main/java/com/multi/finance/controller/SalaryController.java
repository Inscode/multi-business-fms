package com.multi.finance.controller;

import com.multi.finance.dto.request.SalaryPaymentRequest;
import com.multi.finance.dto.request.SalaryRecipientRequest;
import com.multi.finance.dto.response.SalaryPaymentResponse;
import com.multi.finance.dto.response.SalaryRecipientResponse;
import com.multi.finance.service.impl.SalaryServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/salary")
@RequiredArgsConstructor
public class SalaryController {

    private final SalaryServiceImpl salaryService;

    @GetMapping("/recipients")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'MAIN_ACCOUNTANT', 'ACCOUNTANT')")
    public ResponseEntity<List<SalaryRecipientResponse>> getActiveRecipients() {
        return ResponseEntity.ok(salaryService.getActiveRecipients());
    }

    @GetMapping("/recipients/all")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'MAIN_ACCOUNTANT')")
    public ResponseEntity<List<SalaryRecipientResponse>> getAllRecipients() {
        return ResponseEntity.ok(salaryService.getAllRecipients());
    }

    @PostMapping("/recipients")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SalaryRecipientResponse> createRecipient(@RequestBody SalaryRecipientRequest req) {
        return ResponseEntity.ok(salaryService.createRecipient(req));
    }

    @PutMapping("/recipients/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SalaryRecipientResponse> updateRecipient(@PathVariable Long id, @RequestBody SalaryRecipientRequest req) {
        return ResponseEntity.ok(salaryService.updateRecipient(id, req));
    }

    @PatchMapping("/recipients/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        salaryService.setRecipientActive(id, false);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/recipients/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> activate(@PathVariable Long id) {
        salaryService.setRecipientActive(id, true);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/payments")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'MAIN_ACCOUNTANT', 'ACCOUNTANT')")
    public ResponseEntity<SalaryPaymentResponse> createPayment(@RequestBody SalaryPaymentRequest req) {
        return ResponseEntity.ok(salaryService.createPayment(req));
    }

    @GetMapping("/payments")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'MAIN_ACCOUNTANT')")
    public ResponseEntity<List<SalaryPaymentResponse>> getAllPayments(
            @RequestParam(required = false) String month) {
        return ResponseEntity.ok(salaryService.getAllPayments(month));
    }

    @GetMapping("/payments/mine")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'MAIN_ACCOUNTANT', 'ACCOUNTANT')")
    public ResponseEntity<List<SalaryPaymentResponse>> getMyPayments() {
        return ResponseEntity.ok(salaryService.getMyPayments());
    }

    @PatchMapping("/payments/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<SalaryPaymentResponse> approve(@PathVariable Long id) {
        return ResponseEntity.ok(salaryService.approve(id));
    }

    @PatchMapping("/payments/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<SalaryPaymentResponse> reject(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.getOrDefault("reason", "") : "";
        return ResponseEntity.ok(salaryService.reject(id, reason));
    }

    @GetMapping("/payments/by-date-range")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'MAIN_ACCOUNTANT')")
    public ResponseEntity<List<SalaryPaymentResponse>> getByDateRange(
            @RequestParam String from,
            @RequestParam String to) {
        return ResponseEntity.ok(salaryService.getPaymentsByDateRange(
                LocalDate.parse(from), LocalDate.parse(to)));
    }

    @GetMapping("/payments/pending-approval")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'MAIN_ACCOUNTANT')")
    public ResponseEntity<List<SalaryPaymentResponse>> getPendingApproval() {
        return ResponseEntity.ok(salaryService.getPendingApprovalPayments());
    }

    @GetMapping("/payments/pending-count")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<Map<String, Long>> getPendingCount() {
        return ResponseEntity.ok(Map.of("count", salaryService.getPendingCount()));
    }
}