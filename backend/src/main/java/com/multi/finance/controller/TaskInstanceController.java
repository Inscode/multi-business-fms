package com.multi.finance.controller;

import com.multi.finance.dto.request.UpdateTaskStatusRequest;
import com.multi.finance.dto.response.TaskInstanceResponse;
import com.multi.finance.service.impl.TaskArchiveService;
import com.multi.finance.service.impl.TaskInstanceServiceImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tasks/instances")
@RequiredArgsConstructor
public class TaskInstanceController {

    private final TaskInstanceServiceImpl instanceService;
    private final TaskArchiveService      archiveService;

    // Get instances for a date (triggers daily generation for today)
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'MAIN_ACCOUNTANT', 'ACCOUNTANT', 'WORKER')")
    public ResponseEntity<List<TaskInstanceResponse>> getForDate(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(instanceService.getForDate(date != null ? date : LocalDate.now()));
    }

    // History view — date range
    @GetMapping("/history")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'MAIN_ACCOUNTANT')")
    public ResponseEntity<List<TaskInstanceResponse>> getHistory(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(instanceService.getHistory(from, to));
    }

    // Update status: complete / move / undo
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'MAIN_ACCOUNTANT', 'ACCOUNTANT', 'WORKER')")
    public ResponseEntity<TaskInstanceResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTaskStatusRequest req) {
        return ResponseEntity.ok(instanceService.updateStatus(id, req));
    }

    // Manually trigger archiving (admin only — useful for first-run or testing)
    @PostMapping("/archive")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<Map<String, String>> runArchive() {
        archiveService.archiveOldInstances();
        return ResponseEntity.ok(Map.of("message", "Archive job completed"));
    }

    // Add an attachment URL (image already uploaded to ImageKit)
    @PostMapping("/{id}/attachments")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'MAIN_ACCOUNTANT', 'ACCOUNTANT', 'WORKER')")
    public ResponseEntity<TaskInstanceResponse> addAttachment(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String imageUrl = body.get("imageUrl");
        if (imageUrl == null || imageUrl.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(instanceService.addAttachment(id, imageUrl));
    }
}
