package com.multi.finance.invoicing.controller;

import com.multi.finance.invoicing.dto.response.InvoiceResponse;
import com.multi.finance.invoicing.enums.CategoryType;
import com.multi.finance.invoicing.service.InvoiceService;
import com.multi.finance.invoicing.service.VenturaExcelParser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'MAIN_ACCOUNTANT')")
@RequestMapping("/api/invoicing/import")
@RequiredArgsConstructor
public class ImportController {

    private final VenturaExcelParser parser;
    private final InvoiceService invoiceService;

    /**
     * Upload a Ventura XLS export. Returns a preview of parsed invoices + any warnings.
     * No data is saved at this stage — use /confirm to actually import.
     */
    @PostMapping("/preview")
    public ResponseEntity<Map<String, Object>> preview(@RequestParam("file") MultipartFile file,
                                                        @RequestParam("category") CategoryType category)
            throws IOException {
        validateImportCategory(category);
        VenturaExcelParser.ParseResult result = parser.parse(file, category);
        return ResponseEntity.ok(Map.of(
                "invoiceCount", result.invoices().size(),
                "warnings", result.warnings(),
                "invoices", result.previews()
        ));
    }

    /**
     * Parse and import all valid blocks. Idempotent if external_ref already exists (skips duplicates).
     */
    @PostMapping("/confirm")
    public ResponseEntity<Map<String, Object>> confirm(@RequestParam("file") MultipartFile file,
                                                        @RequestParam("category") CategoryType category,
                                                        @RequestParam(required = false, defaultValue = "false") boolean skipExisting,
                                                        Authentication auth) throws IOException {
        validateImportCategory(category);
        VenturaExcelParser.ParseResult result = parser.parse(file, category);

        List<InvoiceResponse> imported = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (var req : result.invoices()) {
            if (req.getLines().isEmpty()) {
                errors.add("Invoice " + req.getExternalRef() + " has no catalog-matched lines — skipped.");
                continue;
            }
            if (req.getCustomerId() == null) {
                errors.add("Invoice " + req.getExternalRef() + " has no customer resolved — skipped.");
                continue;
            }
            try {
                imported.add(invoiceService.create(req, auth.getName()));
            } catch (Exception e) {
                errors.add("Invoice " + req.getExternalRef() + " failed: " + e.getMessage());
            }
        }

        return ResponseEntity.ok(Map.of(
                "imported", imported.size(),
                "warnings", result.warnings(),
                "errors", errors
        ));
    }

    private void validateImportCategory(CategoryType category) {
        if (category != CategoryType.RAINCO && category != CategoryType.STATIONERY) {
            throw new IllegalArgumentException("Import category must be RAINCO or STATIONERY");
        }
    }
}
