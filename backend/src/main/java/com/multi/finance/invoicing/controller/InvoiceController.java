package com.multi.finance.invoicing.controller;

import com.multi.finance.invoicing.dto.request.InvoiceRequest;
import com.multi.finance.invoicing.dto.request.QuoteRequest;
import com.multi.finance.invoicing.dto.response.QuoteResponse;
import com.multi.finance.invoicing.dto.response.InvoicePrintResponse;
import com.multi.finance.invoicing.dto.response.InvoiceResponse;
import com.multi.finance.invoicing.dto.response.InvoiceSummaryResponse;
import com.multi.finance.invoicing.enums.InvoiceMethod;
import com.multi.finance.invoicing.enums.InvoiceSource;
import com.multi.finance.invoicing.service.InvoiceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'MAIN_ACCOUNTANT')")
@RequestMapping("/api/invoicing/invoices")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService invoiceService;

    @PostMapping
    public ResponseEntity<InvoiceResponse> create(@Valid @RequestBody InvoiceRequest req,
                                                   Authentication auth) {
        return ResponseEntity.ok(invoiceService.create(req, auth.getName()));
    }

    /** Prices a draft invoice without saving it, so the discount shows while building. */
    @PostMapping("/quote")
    public ResponseEntity<QuoteResponse> quote(@Valid @RequestBody QuoteRequest req) {
        return ResponseEntity.ok(invoiceService.quote(req));
    }

    /** Admin may rewrite lines, quantities, free issue, discounts and customer. */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<InvoiceResponse> update(@PathVariable Long id,
                                                   @Valid @RequestBody InvoiceRequest req,
                                                   Authentication auth) {
        return ResponseEntity.ok(invoiceService.update(id, req, auth.getName()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<InvoiceResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(invoiceService.getById(id));
    }

    @PostMapping("/{id}/print")
    public ResponseEntity<InvoicePrintResponse> print(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(invoiceService.printInvoice(id, auth.getName()));
    }

    // ── Admin review of newly entered invoices ───────────────────────────

    @GetMapping("/review")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<InvoiceSummaryResponse>> reviewQueue(
            @RequestParam(required = false) Boolean reviewed,
            @RequestParam(required = false) InvoiceSource source,
            @RequestParam(required = false, defaultValue = "false") boolean changedOnly,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String search,
            Pageable pageable) {
        return ResponseEntity.ok(
                invoiceService.reviewQueue(reviewed, source, changedOnly, from, to, search, pageable));
    }

    @GetMapping("/review/count")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Long>> pendingReviewCount() {
        return ResponseEntity.ok(Map.of("count", invoiceService.pendingReviewCount()));
    }

    @PatchMapping("/{id}/review")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<InvoiceSummaryResponse> setReviewed(
            @PathVariable Long id,
            @RequestParam(defaultValue = "true") boolean reviewed,
            Authentication auth) {
        return ResponseEntity.ok(invoiceService.setReviewed(id, reviewed, auth.getName()));
    }

    @PatchMapping("/review/bulk")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Integer>> setReviewedBulk(
            @RequestBody List<Long> ids,
            @RequestParam(defaultValue = "true") boolean reviewed,
            Authentication auth) {
        return ResponseEntity.ok(
                Map.of("updated", invoiceService.setReviewedBulk(ids, reviewed, auth.getName())));
    }

    @GetMapping
    public ResponseEntity<Page<InvoiceSummaryResponse>> search(
            @RequestParam(required = false) InvoiceMethod method,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String search,
            Pageable pageable) {
        return ResponseEntity.ok(invoiceService.search(method, from, to, search, pageable));
    }
}
