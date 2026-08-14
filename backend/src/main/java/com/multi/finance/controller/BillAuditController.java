package com.multi.finance.controller;

import com.multi.finance.dto.request.BillAuditMarkRequest;
import com.multi.finance.dto.response.BillAuditRowResponse;
import com.multi.finance.dto.response.BillAuditSessionResponse;
import com.multi.finance.service.impl.BillAuditServiceImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/bill-audit")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'MAIN_ACCOUNTANT')")
public class BillAuditController {

    private final BillAuditServiceImpl service;

    /** Opens your own sweep for the month, or returns the one you already have open. */
    @PostMapping("/sessions")
    public ResponseEntity<BillAuditSessionResponse> openSession(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate month) {
        return ResponseEntity.ok(service.openSession(month));
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<BillAuditSessionResponse>> listSessions() {
        return ResponseEntity.ok(service.listSessions());
    }

    @GetMapping("/sessions/{id}/rows")
    public ResponseEntity<List<BillAuditRowResponse>> getRows(@PathVariable Long id) {
        return ResponseEntity.ok(service.getRows(id));
    }

    /** Only the sweep's owner (or an admin) may mark — others see it read-only. */
    @PostMapping("/mark")
    public ResponseEntity<BillAuditRowResponse> mark(@Valid @RequestBody BillAuditMarkRequest req) {
        return ResponseEntity.ok(service.mark(req));
    }

    @PatchMapping("/sessions/{id}/close")
    public ResponseEntity<BillAuditSessionResponse> close(@PathVariable Long id) {
        return ResponseEntity.ok(service.closeSession(id));
    }
}
