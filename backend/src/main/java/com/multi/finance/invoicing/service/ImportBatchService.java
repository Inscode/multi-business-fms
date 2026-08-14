package com.multi.finance.invoicing.service;

import com.multi.finance.invoicing.dto.response.ImportBatchResponse;
import com.multi.finance.invoicing.entity.ImportBatch;
import com.multi.finance.invoicing.entity.Invoice;
import com.multi.finance.invoicing.entity.InvoiceLine;
import com.multi.finance.invoicing.enums.CategoryType;
import com.multi.finance.invoicing.repository.ImportBatchRepository;
import com.multi.finance.invoicing.repository.InvoiceRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/**
 * Totals the products on an import run so the figures can be checked against the
 * agent's summary bill.
 *
 * Invoices can be excluded from the totals without changing anything — the check is
 * often against a summary that covers only part of what was uploaded.
 */
@Service
@RequiredArgsConstructor
public class ImportBatchService {

    private final ImportBatchRepository batchRepo;
    private final InvoiceRepository invoiceRepo;

    @Transactional
    public ImportBatch open(CategoryType category, String fileName, String importedBy) {
        ImportBatch batch = new ImportBatch();
        batch.setCategory(category);
        batch.setFileName(fileName);
        batch.setImportedBy(importedBy);
        return batchRepo.save(batch);
    }

    /**
     * Continues an existing batch so several files can be checked against one summary
     * bill. The category must match — a batch mixing Rainco and Stationery could not be
     * compared against anything.
     */
    @Transactional
    public ImportBatch openOrContinue(Long batchId, CategoryType category, String fileName,
                                       String importedBy) {
        if (batchId == null) return open(category, fileName, importedBy);

        ImportBatch existing = batchRepo.findById(batchId)
                .orElseThrow(() -> new EntityNotFoundException("Import batch not found: " + batchId));
        if (existing.getCategory() != category) {
            throw new IllegalArgumentException(
                    "That batch holds " + existing.getCategory() + " invoices — a batch cannot mix "
                    + "categories, so this " + category + " file needs a new one.");
        }
        // Keep a trace of every file that fed the batch.
        if (fileName != null && !fileName.isBlank()) {
            String names = existing.getFileName();
            if (names == null || names.isBlank()) existing.setFileName(fileName);
            else if (!names.contains(fileName)) existing.setFileName(names + ", " + fileName);
        }
        return batchRepo.save(existing);
    }

    /** @param added invoices imported by this run, on top of whatever the batch already held */
    @Transactional
    public void close(ImportBatch batch, int added) {
        int existing = batch.getInvoiceCount() == null ? 0 : batch.getInvoiceCount();
        batch.setInvoiceCount(existing + added);
        batchRepo.save(batch);
    }

    /** Batches are only worth keeping when something actually imported. */
    @Transactional
    public void discardIfEmpty(ImportBatch batch) {
        if (batch.getInvoiceCount() == null || batch.getInvoiceCount() == 0) {
            batchRepo.delete(batch);
        }
    }

    @Transactional(readOnly = true)
    public List<ImportBatchResponse> listRecent() {
        return batchRepo.findTop50ByOrderByImportedAtDesc().stream()
                .map(b -> ImportBatchResponse.builder()
                        .id(b.getId())
                        .category(b.getCategory())
                        .fileName(b.getFileName())
                        .importedBy(b.getImportedBy())
                        .importedAt(b.getImportedAt())
                        .invoiceCount(b.getInvoiceCount())
                        .build())
                .toList();
    }

    /**
     * @param excludedInvoiceIds invoices to leave out of the product totals — they are
     *                           still listed, just not counted
     */
    @Transactional(readOnly = true)
    public ImportBatchResponse summary(Long batchId, Set<Long> excludedInvoiceIds) {
        ImportBatch batch = batchRepo.findById(batchId)
                .orElseThrow(() -> new EntityNotFoundException("Import batch not found: " + batchId));

        Set<Long> excluded = excludedInvoiceIds == null ? Set.of() : excludedInvoiceIds;
        List<Invoice> invoices = invoiceRepo.findByImportBatchIdWithDetails(batchId);

        Map<Long, ImportBatchResponse.ProductLine> products = new LinkedHashMap<>();
        List<ImportBatchResponse.BatchInvoice> rows = new ArrayList<>();
        int totalQty = 0, totalFree = 0;
        BigDecimal totalValue = BigDecimal.ZERO;

        for (Invoice inv : invoices) {
            boolean included = !excluded.contains(inv.getId());

            BigDecimal invoiceValue = inv.getLines().stream()
                    .map(InvoiceLine::getValue)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            rows.add(ImportBatchResponse.BatchInvoice.builder()
                    .id(inv.getId())
                    .invoiceNo(inv.getInvoiceNo())
                    .externalRef(inv.getExternalRef())
                    .customerName(inv.getCustomer().getName())
                    .netTotal(invoiceValue)
                    .included(included)
                    .build());

            if (!included) continue;

            for (InvoiceLine l : inv.getLines()) {
                int free = l.getFreeQty() == null ? 0 : l.getFreeQty();
                products.compute(l.getItem().getId(), (k, existing) -> {
                    if (existing == null) {
                        return ImportBatchResponse.ProductLine.builder()
                                .itemId(l.getItem().getId())
                                .itemCode(l.getItem().getItemCode())
                                .description(l.getItem().getDescription())
                                .brandName(l.getBrand().getName())
                                .qty(l.getQty())
                                .freeQty(free)
                                .value(l.getValue())
                                .build();
                    }
                    existing.setQty(existing.getQty() + l.getQty());
                    existing.setFreeQty(existing.getFreeQty() + free);
                    existing.setValue(existing.getValue().add(l.getValue()));
                    return existing;
                });
                totalQty += l.getQty();
                totalFree += free;
                totalValue = totalValue.add(l.getValue());
            }
        }

        List<ImportBatchResponse.ProductLine> productList = new ArrayList<>(products.values());
        productList.sort(Comparator.comparing(ImportBatchResponse.ProductLine::getItemCode));

        return ImportBatchResponse.builder()
                .id(batch.getId())
                .category(batch.getCategory())
                .fileName(batch.getFileName())
                .importedBy(batch.getImportedBy())
                .importedAt(batch.getImportedAt())
                .invoiceCount(batch.getInvoiceCount())
                .invoices(rows)
                .products(productList)
                .totalQty(totalQty)
                .totalFreeQty(totalFree)
                .totalValue(totalValue)
                .build();
    }
}
