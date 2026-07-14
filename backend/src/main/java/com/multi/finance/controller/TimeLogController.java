package com.multi.finance.controller;

import com.multi.finance.dto.request.AttendanceRequest;
import com.multi.finance.dto.request.HolidayRequest;
import com.multi.finance.dto.request.TimeEntryRequest;
import com.multi.finance.dto.response.CompanyHolidayResponse;
import com.multi.finance.dto.response.WorkerAttendanceResponse;
import com.multi.finance.dto.response.WorkerAverageHoursResponse;
import com.multi.finance.dto.response.WorkerResponse;
import com.multi.finance.dto.response.WorkerTimeEntryResponse;
import com.multi.finance.service.impl.TimeLogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/admin/time-log")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class TimeLogController {

    private final TimeLogService timeLogService;

    // ── Average hours report ──────────────────────────────────────────

    @GetMapping("/average")
    public ResponseEntity<List<WorkerAverageHoursResponse>> getAverageHours(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(timeLogService.getAverageHours(from, to));
    }

    // ── Attendance ────────────────────────────────────────────────────

    @PostMapping("/attendance")
    public ResponseEntity<WorkerAttendanceResponse> recordAttendance(
            @Valid @RequestBody AttendanceRequest req) {
        return ResponseEntity.ok(timeLogService.recordAttendance(req));
    }

    @GetMapping("/attendance")
    public ResponseEntity<List<WorkerAttendanceResponse>> getAttendanceForDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(timeLogService.getAttendanceForDate(date));
    }

    @GetMapping("/attendance/worker/{workerId}")
    public ResponseEntity<List<WorkerAttendanceResponse>> getWorkerAttendance(
            @PathVariable Long workerId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(timeLogService.getWorkerAttendance(workerId, from, to));
    }

    // ── Time entries ──────────────────────────────────────────────────

    @PostMapping("/entries")
    public ResponseEntity<WorkerTimeEntryResponse> addTimeEntry(
            @Valid @RequestBody TimeEntryRequest req) {
        return ResponseEntity.ok(timeLogService.addTimeEntry(req));
    }

    @PutMapping("/entries/{id}")
    public ResponseEntity<WorkerTimeEntryResponse> updateTimeEntry(
            @PathVariable Long id,
            @Valid @RequestBody TimeEntryRequest req) {
        return ResponseEntity.ok(timeLogService.updateTimeEntry(id, req));
    }

    @DeleteMapping("/entries/{id}")
    public ResponseEntity<Void> deleteTimeEntry(@PathVariable Long id) {
        timeLogService.deleteTimeEntry(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/entries")
    public ResponseEntity<List<WorkerTimeEntryResponse>> getEntriesForDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(timeLogService.getTimeEntriesForDate(date));
    }

    @GetMapping("/entries/worker/{workerId}")
    public ResponseEntity<List<WorkerTimeEntryResponse>> getWorkerEntries(
            @PathVariable Long workerId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(timeLogService.getWorkerTimeEntries(workerId, date));
    }

    // ── Participants (toggle time-log active) ─────────────────────────

    @PatchMapping("/workers/{id}/toggle-log")
    public ResponseEntity<WorkerResponse> toggleWorkerTimeLog(@PathVariable Long id) {
        return ResponseEntity.ok(timeLogService.toggleTimeLogActive(id));
    }

    // ── Holidays ──────────────────────────────────────────────────────

    @PostMapping("/holidays")
    public ResponseEntity<CompanyHolidayResponse> createHoliday(
            @Valid @RequestBody HolidayRequest req) {
        return ResponseEntity.ok(timeLogService.createHoliday(req));
    }

    @GetMapping("/holidays")
    public ResponseEntity<List<CompanyHolidayResponse>> getHolidays(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(timeLogService.getHolidays(from, to));
    }

    @DeleteMapping("/holidays/{id}")
    public ResponseEntity<Void> deleteHoliday(@PathVariable Long id) {
        timeLogService.deleteHoliday(id);
        return ResponseEntity.noContent().build();
    }
}
