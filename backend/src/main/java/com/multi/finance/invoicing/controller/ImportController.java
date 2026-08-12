package com.multi.finance.invoicing.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.multi.finance.invoicing.dto.response.InvoiceResponse;
import com.multi.finance.invoicing.enums.CategoryType;
import com.multi.finance.invoicing.entity.Item;
import com.multi.finance.invoicing.enums.InvoiceSource;
import com.multi.finance.invoicing.service.ImportBatchService;
import com.multi.finance.invoicing.service.InvoiceService;
import com.multi.finance.invoicing.service.VenturaExcelParser;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'MAIN_ACCOUNTANT')")
@RequestMapping("/api/invoicing/import")
@RequiredArgsConstructor
public class ImportController {

    private final VenturaExcelParser parser;
    private final InvoiceService invoiceService;
    private final ObjectMapper objectMapper;
    private final ImportBatchService batchService;
    private final com.multi.finance.invoicing.repository.ItemRepository itemRepo;

    /**
     * The umbrella given free against stationery invoices. Tried in order, so the catalog
     * can carry it under either code.
     */
    private static final List<String> FREE_UMBRELLA_CODES =
            com.multi.finance.invoicing.service.FreeIssuePolicy.FREE_UMBRELLA_CODES;

    /**
     * Upload a Ventura XLS export. Returns a preview of parsed invoices + any warnings.
     * No data is saved at this stage — use /confirm to actually import.
     */
    @PostMapping("/preview")
    public ResponseEntity<Map<String, Object>> preview(@RequestParam("files") MultipartFile[] files,
                                                        @RequestParam("category") CategoryType category)
            throws IOException {
        validateImportCategory(category);

        // One summary load often arrives as several files, so they are previewed together
        // and totalled as one.
        List<VenturaExcelParser.ParsedInvoice> all = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (MultipartFile file : files) {
            VenturaExcelParser.ParseResult result = parser.parse(file, category);
            all.addAll(result.invoices());
            for (String w : result.warnings()) warnings.add(file.getOriginalFilename() + ": " + w);
        }

        // The same invoice appearing in two of the chosen files would import once and then
        // collide, so it is called out here rather than surfacing as a failure later.
        Set<String> seen = new HashSet<>();
        for (var p : all) {
            String ref = p.request().getExternalRef();
            if (ref != null && !seen.add(ref)) {
                warnings.add("Invoice " + ref + " appears in more than one of the chosen files — "
                        + "it will be imported once.");
            }
        }

        long blocked = all.stream().filter(VenturaExcelParser.ParsedInvoice::blocked).count();
        long duplicates = all.stream().filter(VenturaExcelParser.ParsedInvoice::alreadyImported).count();
        long importable = all.stream().filter(p -> !p.blocked() && !p.alreadyImported()).count();

        return ResponseEntity.ok(Map.of(
                "fileCount", files.length,
                "invoiceCount", all.size(),
                "blockedCount", blocked,
                "duplicateCount", duplicates,
                "importableCount", importable,
                "warnings", warnings,
                "invoices", all.stream().map(VenturaExcelParser.ParsedInvoice::preview).toList()
        ));
    }

    /**
     * Parse and import all valid blocks. Idempotent if external_ref already exists (skips duplicates).
     */
    @PostMapping("/confirm")
    public ResponseEntity<Map<String, Object>> confirm(@RequestParam("files") MultipartFile[] files,
                                                        @RequestParam("category") CategoryType category,
                                                        @RequestParam(required = false) String customerOverrides,
                                                        @RequestParam(required = false) String freeUmbrellas,
                                                        @RequestParam(required = false) Long batchId,
                                                        @RequestParam(required = false) String excludeRefs,
                                                        @RequestParam(required = false) String numberOverrides,
                                                        @RequestParam(required = false, defaultValue = "false") boolean skipExisting,
                                                        Authentication auth) throws IOException {
        validateImportCategory(category);

        List<VenturaExcelParser.ParsedInvoice> all = new ArrayList<>();
        List<String> parseWarnings = new ArrayList<>();
        StringBuilder fileNames = new StringBuilder();
        for (MultipartFile file : files) {
            VenturaExcelParser.ParseResult result = parser.parse(file, category);
            all.addAll(result.invoices());
            for (String w : result.warnings()) parseWarnings.add(file.getOriginalFilename() + ": " + w);
            if (fileNames.length() > 0) fileNames.append(", ");
            fileNames.append(file.getOriginalFilename());
        }

        // Invoices are frequently raised under a name that isn't the real buyer's, so the
        // accountant repoints them in the preview. Keyed by the agent's invoice number,
        // which is what the preview rows are identified by.
        Map<String, Long> overrides = parseOverrides(customerOverrides);

        // Stationery invoices sometimes carry a free umbrella that isn't on the agent's
        // sheet. Keyed by invoice number, same as the customer picks.
        Map<String, Long> umbrellas = parseOverrides(freeUmbrellas);

        // A summary load often covers only part of what the files hold, so invoices the
        // accountant dropped in the preview are left out entirely.
        Set<String> excluded = parseRefList(excludeRefs);

        // An admin may correct the number the reference produced. Keyed by invoice number,
        // same as the other per-invoice choices.
        Map<String, String> numbers = parseStringMap(numberOverrides);
        Item freeUmbrella = umbrellas.isEmpty() ? null : findFreeUmbrella();

        // One batch per press of Import, so the products can be totalled against the
        // agent's summary bill afterwards.
        // Continuing a batch lets several files be summarised together against one
        // summary bill; without an id this starts a fresh one.
        var batch = batchService.openOrContinue(batchId, category, fileNames.toString(),
                                                 auth.getName());

        List<InvoiceResponse> imported = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        // Attached to bills already entered by hand: stock moved, no second bill raised.
        List<String> stockOnly = new ArrayList<>();

        // Guards the same invoice appearing in two of the chosen files.
        Set<String> importedRefs = new HashSet<>();

        for (var parsed : all) {
            var req = parsed.request();

            if (req.getExternalRef() != null && excluded.contains(req.getExternalRef())) {
                continue;   // removed in the preview — not imported, not reported as a problem
            }

            if (req.getExternalRef() != null && !importedRefs.add(req.getExternalRef())) {
                skipped.add(req.getExternalRef());
                continue;
            }

            Long override = req.getExternalRef() != null ? overrides.get(req.getExternalRef()) : null;
            if (override != null) {
                // originalCustomerName is left as parsed: it is the "changed from" the admin sees.
                req.setCustomerId(override);
            }

            // An invoice with an unmatched line is never imported — a partial import would
            // understate its value and move stock for only part of the goods.
            if (parsed.blocked()) {
                errors.add("Invoice " + req.getExternalRef() + " BLOCKED — could not match: "
                        + String.join(", ", parsed.unmatchedCodes())
                        + ". Fix the item code or price in the catalog, then re-import.");
                continue;
            }
            // Re-uploading a file that was already loaded is routine, not a failure — the
            // invoice is left exactly as it was and simply reported as skipped.
            if (parsed.alreadyImported()) {
                skipped.add(req.getExternalRef());
                continue;
            }
            if (req.getLines().isEmpty()) {
                errors.add("Invoice " + req.getExternalRef() + " has no catalog-matched lines — skipped.");
                continue;
            }
            if (req.getCustomerId() == null) {
                errors.add("Invoice " + req.getExternalRef()
                        + " has no customer — pick one in the preview, then import again.");
                continue;
            }

            String numberOverride = req.getExternalRef() == null
                    ? null : numbers.get(req.getExternalRef());
            if (numberOverride != null) {
                if (numberOverride.isBlank()) {
                    errors.add("Invoice " + req.getExternalRef()
                            + " has a blank invoice number — every invoice needs one.");
                    continue;
                }
                req.setInvoiceNoOverride(numberOverride.trim());
            }

            Long umbrellaQty = req.getExternalRef() == null ? null : umbrellas.get(req.getExternalRef());
            if (umbrellaQty != null && umbrellaQty > 0) {
                if (freeUmbrella == null) {
                    errors.add("Invoice " + req.getExternalRef() + " — the free umbrella item ("
                            + String.join(" or ", FREE_UMBRELLA_CODES) + ") is not in the catalog.");
                    continue;
                }
                // A free-only line: no paid quantity, so it adds nothing to the invoice
                // value, but the umbrellas still leave stock.
                var gift = new com.multi.finance.invoicing.dto.request.InvoiceLineRequest();
                gift.setItemId(freeUmbrella.getId());
                gift.setQty(0);
                gift.setFreeQty(umbrellaQty.intValue());
                req.getLines().add(gift);
            }
            try {
                var created = invoiceService.create(req, auth.getName(), InvoiceSource.IMPORT,
                                                     batch.getId());
                imported.add(created);
                if (created.isBillLinkedExisting()) stockOnly.add(created.getInvoiceNo());
            } catch (DataIntegrityViolationException e) {
                String constraint = constraintOf(e);
                if ("uq_inv_external_ref_method".equals(constraint)) {
                    // Race with a second import between the parse-time check and the insert.
                    skipped.add(req.getExternalRef());
                } else {
                    errors.add(explainClash(req.getExternalRef(), constraint, e));
                }
            } catch (Exception e) {
                errors.add("Invoice " + req.getExternalRef() + " failed: " + e.getMessage());
            }
        }

        batchService.close(batch, imported.size());
        batchService.discardIfEmpty(batch);

        return ResponseEntity.ok(Map.of(
                "batchId", imported.isEmpty() ? 0L : batch.getId(),
                "imported", imported.size(),
                "blocked", all.stream().filter(VenturaExcelParser.ParsedInvoice::blocked).count(),
                "skipped", skipped.size(),
                "skippedRefs", skipped,
                "stockOnly", stockOnly.size(),
                "stockOnlyRefs", stockOnly,
                "warnings", parseWarnings,
                "errors", errors
        ));
    }

    // ── Batch summaries, for checking against the agent's summary bill ──

    @GetMapping("/batches")
    public ResponseEntity<List<com.multi.finance.invoicing.dto.response.ImportBatchResponse>> batches() {
        return ResponseEntity.ok(batchService.listRecent());
    }

    @GetMapping("/batches/{id}/summary")
    public ResponseEntity<com.multi.finance.invoicing.dto.response.ImportBatchResponse> batchSummary(
            @PathVariable Long id,
            @RequestParam(required = false) java.util.Set<Long> exclude) {
        return ResponseEntity.ok(batchService.summary(id, exclude));
    }

    /** The database constraint that rejected the row, or null if it can't be identified. */
    private String constraintOf(DataIntegrityViolationException e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof org.hibernate.exception.ConstraintViolationException cve
                    && cve.getConstraintName() != null) {
                return cve.getConstraintName();
            }
        }
        // The driver is runtime-scoped, so fall back to reading the server's own wording.
        String msg = e.getMostSpecificCause().getMessage();
        if (msg == null) return null;
        var m = java.util.regex.Pattern.compile("constraint \"([^\"]+)\"").matcher(msg);
        return m.find() ? m.group(1) : null;
    }

    /**
     * Names what actually clashed. A generic "clashes with existing data" tells nobody
     * anything — the constraint is the whole diagnosis, so it is always reported.
     */
    private String explainClash(String ref, String constraint, DataIntegrityViolationException e) {
        String known = switch (constraint == null ? "" : constraint) {
            case "uk_bills_bill_number_business", "uk_bills_bill_number" ->
                    "a bill numbered INV-" + ref + " already exists in the bills section. "
                    + "Delete or renumber that bill, then import again.";
            case "inv_invoices_invoice_no_key" ->
                    "our own invoice number was already taken — the invoice number sequence is "
                    + "behind the data. Reset it with setval() and import again.";
            case "fk_inv_invoices_bill" ->
                    "the bill it points at no longer exists.";
            default -> null;
        };
        if (known != null) return "Invoice " + ref + " — " + known;

        // Unrecognised: pass the database's own words through rather than inventing softer ones.
        String detail = e.getMostSpecificCause().getMessage();
        if (detail != null) detail = detail.lines().findFirst().orElse(detail).trim();
        return "Invoice " + ref + " could not be saved"
                + (constraint != null ? " — constraint " + constraint : "")
                + (detail != null ? ": " + detail : "");
    }

    /** {@code ["INV-1","INV-2"]} — invoices the accountant removed from the load. */
    private Set<String> parseRefList(String json) {
        if (json == null || json.isBlank()) return Set.of();
        try {
            return new HashSet<>(objectMapper.readValue(json, new TypeReference<List<String>>() {}));
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not read the removed invoices: " + e.getMessage());
        }
    }

    /** {@code {"BDSL/13271": "SYS-13271"}} — numbers an admin corrected. */
    private Map<String, String> parseStringMap(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not read the invoice numbers: " + e.getMessage());
        }
    }

    private Item findFreeUmbrella() {
        for (String code : FREE_UMBRELLA_CODES) {
            var found = itemRepo.findByItemCode(code);
            if (found.isPresent()) return found.get();
        }
        return null;
    }

    /** {@code {"INV-123": 42}} — agent invoice number to the customer chosen for it. */
    private Map<String, Long> parseOverrides(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Long>>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not read the customer selections: " + e.getMessage());
        }
    }

    private void validateImportCategory(CategoryType category) {
        if (category != CategoryType.RAINCO && category != CategoryType.STATIONERY) {
            throw new IllegalArgumentException("Import category must be RAINCO or STATIONERY");
        }
    }
}
