package com.multi.finance.controller;

import com.multi.finance.dto.request.TaskClarificationRequest;
import com.multi.finance.dto.response.TaskClarificationResponse;
import com.multi.finance.service.impl.TaskClarificationServiceImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tasks/instances/{instanceId}/clarifications")
@RequiredArgsConstructor
public class TaskClarificationController {

    private final TaskClarificationServiceImpl clarificationService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'MAIN_ACCOUNTANT', 'ACCOUNTANT', 'WORKER')")
    public ResponseEntity<List<TaskClarificationResponse>> getForInstance(@PathVariable Long instanceId) {
        return ResponseEntity.ok(clarificationService.getForInstance(instanceId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'MAIN_ACCOUNTANT', 'ACCOUNTANT', 'WORKER')")
    public ResponseEntity<TaskClarificationResponse> add(
            @PathVariable Long instanceId,
            @Valid @RequestBody TaskClarificationRequest req) {
        return ResponseEntity.ok(clarificationService.addClarification(instanceId, req));
    }
}
