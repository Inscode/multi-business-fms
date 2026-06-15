package com.multi.finance.controller;

import com.multi.finance.dto.request.TaskTemplateRequest;
import com.multi.finance.dto.response.TaskTemplateResponse;
import com.multi.finance.service.impl.TaskTemplateServiceImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tasks/templates")
@RequiredArgsConstructor
public class TaskTemplateController {

    private final TaskTemplateServiceImpl templateService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<List<TaskTemplateResponse>> getAll() {
        return ResponseEntity.ok(templateService.getAll());
    }

    // Inactive ON_DEMAND templates available for activation
    @GetMapping("/on-demand/inactive")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'MAIN_ACCOUNTANT', 'ACCOUNTANT', 'WORKER')")
    public ResponseEntity<List<TaskTemplateResponse>> getInactiveOnDemand() {
        return ResponseEntity.ok(templateService.getInactiveOnDemand());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<TaskTemplateResponse> create(@Valid @RequestBody TaskTemplateRequest req) {
        return ResponseEntity.ok(templateService.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<TaskTemplateResponse> update(@PathVariable Long id,
                                                       @Valid @RequestBody TaskTemplateRequest req) {
        return ResponseEntity.ok(templateService.update(id, req));
    }

    // Toggle active for DAILY templates
    @PatchMapping("/{id}/toggle-active")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<TaskTemplateResponse> toggleActive(@PathVariable Long id) {
        return ResponseEntity.ok(templateService.toggleActive(id));
    }

    // Activate an ON_DEMAND template for today
    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'MAIN_ACCOUNTANT')")
    public ResponseEntity<TaskTemplateResponse> activateOnDemand(@PathVariable Long id) {
        return ResponseEntity.ok(templateService.activateOnDemand(id));
    }

    // Deactivate an ON_DEMAND template
    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'MAIN_ACCOUNTANT')")
    public ResponseEntity<TaskTemplateResponse> deactivateOnDemand(@PathVariable Long id) {
        return ResponseEntity.ok(templateService.deactivateOnDemand(id));
    }
}
