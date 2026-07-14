package com.multi.finance.controller;

import com.multi.finance.dto.request.CollectionNoteBulkRequest;
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

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<CollectionNoteResponse> create(
            @Valid @RequestBody CollectionNoteRequest request) {
        return ResponseEntity.ok(collectionNoteService.create(request));
    }

    @PostMapping("/bulk")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<List<CollectionNoteResponse>> createBulk(
            @Valid @RequestBody CollectionNoteBulkRequest request) {
        return ResponseEntity.ok(collectionNoteService.createBulk(request));
    }

    // Owner/Admin sees their own collection notes
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

    // Admin sees all collection notes
    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<CollectionNoteResponse>> getAll() {
        return ResponseEntity.ok(collectionNoteService.getAll());
    }

    // Admin edits a collection note
    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CollectionNoteResponse> update(
            @PathVariable Long id,
            @RequestBody CollectionNoteRequest request) {
        return ResponseEntity.ok(collectionNoteService.update(id, request));
    }

    // Admin deletes a collection note
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        collectionNoteService.delete(id);
        return ResponseEntity.noContent().build();
    }
}