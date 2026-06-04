package com.multi.finance.controller;

import com.multi.finance.dto.request.BillReminderRequest;
import com.multi.finance.dto.response.BillReminderResponse;
import com.multi.finance.service.impl.BillReminderServiceImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bill-reminders")
@RequiredArgsConstructor
public class BillReminderController {

    private final BillReminderServiceImpl reminderService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'MAIN_ACCOUNTANT')")
    public ResponseEntity<BillReminderResponse> create(@Valid @RequestBody BillReminderRequest request) {
        return ResponseEntity.ok(reminderService.create(request));
    }

    @GetMapping("/today")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'MAIN_ACCOUNTANT')")
    public ResponseEntity<List<BillReminderResponse>> getTodayAndOverdue() {
        return ResponseEntity.ok(reminderService.getTodayAndOverdue());
    }

    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'MAIN_ACCOUNTANT')")
    public ResponseEntity<List<BillReminderResponse>> getPending() {
        return ResponseEntity.ok(reminderService.getPendingReminders());
    }

    @GetMapping("/bill/{billId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'MAIN_ACCOUNTANT')")
    public ResponseEntity<List<BillReminderResponse>> getByBill(@PathVariable Long billId) {
        return ResponseEntity.ok(reminderService.getByBill(billId));
    }

    @PatchMapping("/{id}/done")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'MAIN_ACCOUNTANT')")
    public ResponseEntity<BillReminderResponse> markDone(@PathVariable Long id) {
        return ResponseEntity.ok(reminderService.markDone(id));
    }

    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'MAIN_ACCOUNTANT')")
    public ResponseEntity<BillReminderResponse> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(reminderService.cancel(id));
    }

    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<BillReminderResponse>> getAll() {
        return ResponseEntity.ok(reminderService.getAll());
    }

    @PatchMapping("/{id}/update")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BillReminderResponse> update(
            @PathVariable Long id,
            @RequestBody BillReminderRequest request) {
        return ResponseEntity.ok(reminderService.update(id, request));
    }
}