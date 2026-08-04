package com.multi.finance.invoicing.controller;

import com.multi.finance.invoicing.dto.request.InvoiceRequest;
import com.multi.finance.invoicing.dto.response.InvoicePrintResponse;
import com.multi.finance.invoicing.dto.response.InvoiceResponse;
import com.multi.finance.invoicing.dto.response.InvoiceSummaryResponse;
import com.multi.finance.invoicing.enums.InvoiceMethod;
import com.multi.finance.invoicing.service.InvoiceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

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

    @GetMapping("/{id}")
    public ResponseEntity<InvoiceResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(invoiceService.getById(id));
    }

    @PostMapping("/{id}/print")
    public ResponseEntity<InvoicePrintResponse> print(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(invoiceService.printInvoice(id, auth.getName()));
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
