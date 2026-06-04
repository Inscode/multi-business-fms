package com.multi.finance.controller;

import com.multi.finance.dto.request.BillChecklistClosingRequest;
import com.multi.finance.dto.request.BillChecklistNoteRequest;
import com.multi.finance.dto.response.BillChecklistDashboardResponse;
import com.multi.finance.dto.response.BillChecklistNoteResponse;
import com.multi.finance.dto.response.BillChecklistVerifyRowResponse;
import com.multi.finance.service.impl.BillChecklistServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/bill-checklist")
@RequiredArgsConstructor
public class BillChecklistController {

    private final BillChecklistServiceImpl service;

    @PostMapping("/notes")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<BillChecklistNoteResponse> addNote(@RequestBody BillChecklistNoteRequest req) {
        return ResponseEntity.ok(service.addNote(req));
    }

    @GetMapping("/notes")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<List<BillChecklistNoteResponse>> getNotesForDate(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(service.getNotesForDate(date != null ? date : LocalDate.now()));
    }

    @DeleteMapping("/notes/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<Void> deleteNote(@PathVariable Long id) {
        service.deleteNote(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/verify")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<List<BillChecklistVerifyRowResponse>> getVerifyView(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(service.getVerifyView(date != null ? date : LocalDate.now()));
    }

    @PostMapping("/close")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<Void> saveClosing(@RequestBody BillChecklistClosingRequest req) {
        service.saveClosing(req);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/dashboard-summary")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<BillChecklistDashboardResponse> getDashboardSummary() {
        return ResponseEntity.ok(service.getDashboardSummary());
    }
}
