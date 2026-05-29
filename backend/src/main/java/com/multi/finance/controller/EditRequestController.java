package com.multi.finance.controller;

import com.multi.finance.dto.request.CreateEditRequestDto;
import com.multi.finance.dto.response.EditRequestResponse;
import com.multi.finance.service.impl.EditRequestServiceImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/edit-requests")
@RequiredArgsConstructor
public class EditRequestController {

    private final EditRequestServiceImpl editRequestService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ACCOUNTANT', 'MAIN_ACCOUNTANT', 'SHOP_ACCOUNTANT')")
    public ResponseEntity<EditRequestResponse> create(@Valid @RequestBody CreateEditRequestDto dto) {
        return ResponseEntity.ok(editRequestService.create(dto));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<EditRequestResponse>> getAll() {
        return ResponseEntity.ok(editRequestService.getAll());
    }

    @GetMapping("/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<EditRequestResponse>> getPending() {
        return ResponseEntity.ok(editRequestService.getPending());
    }

    @PatchMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EditRequestResponse> approve(@PathVariable Long id) {
        return ResponseEntity.ok(editRequestService.approve(id));
    }

    @PatchMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EditRequestResponse> reject(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(editRequestService.reject(id, body.get("reason")));
    }
}