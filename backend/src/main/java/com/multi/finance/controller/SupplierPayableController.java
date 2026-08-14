package com.multi.finance.controller;

import com.multi.finance.dto.request.SupplierPayableRequest;
import com.multi.finance.dto.response.SupplierPayableResponse;
import com.multi.finance.entity.SupplierPayable;
import com.multi.finance.entity.User;
import com.multi.finance.repository.SupplierPayableRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Obligations to the principals that this system has no GRN for — cheques already
 * written against older purchases. Entered by hand so the cash-flow forecast
 * reflects what actually has to be met.
 */
@RestController
@RequestMapping("/api/supplier-payables")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'MAIN_ACCOUNTANT')")
public class SupplierPayableController {

    private final SupplierPayableRepository repository;

    @GetMapping
    public List<SupplierPayableResponse> list() {
        return repository.findAllByOrderByDueDateAsc().stream().map(this::toResponse).toList();
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<SupplierPayableResponse> create(@Valid @RequestBody SupplierPayableRequest req) {
        SupplierPayable p = SupplierPayable.builder()
                .business(req.getBusiness())
                .supplierName(req.getSupplierName())
                .description(req.getDescription())
                .amount(req.getAmount())
                .dueDate(req.getDueDate())
                .chequeNumber(req.getChequeNumber())
                .bankName(req.getBankName())
                .notes(req.getNotes())
                .settled(false)
                .createdBy(currentUser())
                .createdAt(LocalDateTime.now())
                .build();
        return ResponseEntity.ok(toResponse(repository.save(p)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<SupplierPayableResponse> update(@PathVariable Long id,
                                                          @Valid @RequestBody SupplierPayableRequest req) {
        SupplierPayable p = get(id);
        p.setBusiness(req.getBusiness());
        p.setSupplierName(req.getSupplierName());
        p.setDescription(req.getDescription());
        p.setAmount(req.getAmount());
        p.setDueDate(req.getDueDate());
        p.setChequeNumber(req.getChequeNumber());
        p.setBankName(req.getBankName());
        p.setNotes(req.getNotes());
        return ResponseEntity.ok(toResponse(repository.save(p)));
    }

    /** Met — drops out of the forecast. */
    @PatchMapping("/{id}/settle")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<SupplierPayableResponse> settle(@PathVariable Long id,
                                                          @RequestParam(required = false) Boolean settled) {
        SupplierPayable p = get(id);
        boolean value = settled == null || settled;
        p.setSettled(value);
        p.setSettledOn(value ? LocalDate.now() : null);
        return ResponseEntity.ok(toResponse(repository.save(p)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        repository.delete(get(id));
        return ResponseEntity.noContent().build();
    }

    private SupplierPayable get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Payable not found"));
    }

    private SupplierPayableResponse toResponse(SupplierPayable p) {
        return SupplierPayableResponse.builder()
                .id(p.getId())
                .business(p.getBusiness().name())
                .supplierName(p.getSupplierName())
                .description(p.getDescription())
                .amount(p.getAmount())
                .dueDate(p.getDueDate())
                .chequeNumber(p.getChequeNumber())
                .bankName(p.getBankName())
                .settled(p.getSettled())
                .settledOn(p.getSettledOn())
                .notes(p.getNotes())
                .createdByName(p.getCreatedBy() != null ? p.getCreatedBy().getFullName() : null)
                .daysUntilDue(ChronoUnit.DAYS.between(LocalDate.now(), p.getDueDate()))
                .build();
    }

    private User currentUser() {
        return (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
