package com.multi.finance.controller;


import com.multi.finance.dto.request.WorkerRequest;
import com.multi.finance.dto.response.WorkerResponse;
import com.multi.finance.enums.WorkerType;
import com.multi.finance.service.impl.WorkerServiceImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.coyote.Response;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.PublicKey;
import java.util.List;

@RestController
@RequestMapping("/api/workers")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class WorkerController {

    private final WorkerServiceImpl workerService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<WorkerResponse> createWorker(
            @Valid @RequestBody WorkerRequest request
            ) {
        return ResponseEntity.ok(workerService.createWorker(request));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'OWNER')")
    public ResponseEntity<List<WorkerResponse>> getAllWorkers() {
        return ResponseEntity.ok(workerService.getAllWorkers());
    }

    @GetMapping("/worker-type/{workerType}")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','OWNER')")
    public ResponseEntity<List<WorkerResponse>> getWorkersByWorkerType(
            @PathVariable WorkerType workerType
    ) {
        return ResponseEntity.ok(workerService.getWorkersByWorkerType(workerType));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<WorkerResponse> updateWorker(
            @PathVariable Long id,
            @Valid @RequestBody WorkerRequest request
    ) {
        return ResponseEntity.ok(workerService.updateWorker(id, request));
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> activateWorker(@PathVariable Long id) {
        workerService.activateWorker(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deactivateWorker(@PathVariable Long id) {
        workerService.deactivateWorker(id);
        return ResponseEntity.noContent().build();
    }

}
