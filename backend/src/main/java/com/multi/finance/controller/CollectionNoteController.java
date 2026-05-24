package com.multi.finance.controller;

import com.multi.finance.dto.request.CollectionNoteRequest;
import com.multi.finance.dto.response.CollectionNoteResponse;
import com.multi.finance.service.impl.CollectionNoteServiceImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/collection-notes")
@RequiredArgsConstructor
public class CollectionNoteController {

    private final CollectionNoteServiceImpl collectionNoteService;

    // Owner marks a bill as collected
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<CollectionNoteResponse> create(
            @Valid @RequestBody CollectionNoteRequest request) {
        return ResponseEntity.ok(collectionNoteService.create(request));
    }

    // Owner sees their own collection notes
    @GetMapping("/my")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<List<CollectionNoteResponse>> getMyNotes() {
        return ResponseEntity.ok(collectionNoteService.getMyNotes());
    }

    // Accountant sees all pending notes that need formal payment entry
    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'MAIN_ACCOUNTANT')")
    public ResponseEntity<List<CollectionNoteResponse>> getPendingNotes() {
        return ResponseEntity.ok(collectionNoteService.getPendingNotes());
    }
}