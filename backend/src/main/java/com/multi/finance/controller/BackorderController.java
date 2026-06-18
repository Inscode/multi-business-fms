package com.multi.finance.controller;

import com.multi.finance.dto.request.BackorderSubmitRequest;
import com.multi.finance.dto.response.BackorderRequestResponse;
import com.multi.finance.dto.response.BillResponse;
import com.multi.finance.service.impl.BackorderServiceImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/backorders")
@RequiredArgsConstructor
public class BackorderController {

    private final BackorderServiceImpl backorderService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','MAIN_ACCOUNTANT')")
    public ResponseEntity<BackorderRequestResponse> submit(@Valid @RequestBody BackorderSubmitRequest req) {
        return ResponseEntity.ok(backorderService.submit(req));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','OWNER','ACCOUNTANT','MAIN_ACCOUNTANT')")
    public ResponseEntity<List<BackorderRequestResponse>> getAll() {
        return ResponseEntity.ok(backorderService.getAll());
    }

    @GetMapping("/bills")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','MAIN_ACCOUNTANT')")
    public ResponseEntity<List<BillResponse>> getActiveBills() {
        return ResponseEntity.ok(backorderService.getActiveRaincoBills());
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> approve(@PathVariable Long id) {
        backorderService.approve(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> reject(@PathVariable Long id,
                                       @RequestBody(required = false) Map<String, String> body) {
        backorderService.reject(id, body != null ? body.get("reason") : null);
        return ResponseEntity.ok().build();
    }
}
