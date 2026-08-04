package com.multi.finance.invoicing.service;

import com.multi.finance.invoicing.dto.request.InvoiceLineRequest;
import com.multi.finance.invoicing.dto.request.InvoiceRequest;
import com.multi.finance.entity.Customer;
import com.multi.finance.invoicing.entity.Item;
import com.multi.finance.invoicing.enums.CategoryType;
import com.multi.finance.invoicing.enums.InvoiceMethod;
import com.multi.finance.invoicing.enums.InvoiceType;
import com.multi.finance.repository.CustomerRepository;
import com.multi.finance.invoicing.repository.ItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses "SODOC02 Invoice Reprint" Ventura Crystal Reports binary XLS exports.
 *
 * Layout (calibrated against a real export — column indices are 0-based data columns,
 * not the label columns, since Crystal Reports merges cells and offsets labels from values):
 *
 *  Per invoice block, anchored on the row where column 0 == "CUS. CODE":
 *    row +0: CUS. CODE  (value col 6)   |  INV. NO   (value col 48)
 *    row +1: CUS. NAME  (value col 6)   |  INV. DATE (value col 48, Excel date serial)
 *    row +2: ADDRESS    (value col 6)
 *    row +4: phone      (value col 6)
 *
 *  Item table (order/spacing after the header varies — driven by content, not fixed offsets):
 *    Item line row: item code (col 0), description (col 6), qty (col 23),
 *                    MRP (col 30), margin% (col 39), WSP (col 46), value (col 55).
 *    Brand group header rows (e.g. "1304 - Rainco Umbrella") and subtotal rows
 *    (label in col 16) have no qty in col 23, so they're naturally skipped by the
 *    item-line test — no special-case handling needed.
 *
 *  Block close: row where col 16 == "NET Invoice Value" (value col 40).
 *  End of report: row where col 0 contains "End Of Report" — stop scanning entirely
 *  (this marker appears once for the whole file, not per invoice).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VenturaExcelParser {

    private static final int COL_VALUE_A        = 6;   // customer code / name / address / phone
    private static final int COL_VALUE_B        = 48;  // invoice no / invoice date / net total

    private static final int COL_ITEM_CODE   = 0;
    private static final int COL_DESCRIPTION = 6;
    private static final int COL_QTY         = 23;
    private static final int COL_MRP         = 30;
    private static final int COL_WSP         = 46;
    private static final int COL_VALUE       = 55;

    private static final int COL_SUBTOTAL_LABEL = 16;
    private static final int COL_SUBTOTAL_VALUE = 40;

    private final ItemRepository itemRepo;
    private final CustomerRepository customerRepo;
    private final DiscountEngineService discountEngine = new DiscountEngineService();

    // open-in-view is off in the FMS — item.getBrand() inside matching needs an open session
    @Transactional(readOnly = true)
    public ParseResult parse(MultipartFile file, CategoryType importCategory) throws IOException {
        List<InvoiceRequest> invoices = new ArrayList<>();
        List<PreviewInvoice> previews = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // Restrict matching to the selected category so a Rainco import can never pick up a
        // Stationery item (or vice versa) — this is what makes "other category won't be there"
        // hold, without needing to inspect each row's category before it's even matched.
        Map<BigDecimal, List<Item>> itemsByWsp = new HashMap<>();
        Map<String, Item> itemsByCode = new HashMap<>();
        for (Item item : itemRepo.findByCategoryAndActiveTrue(importCategory)) {
            BigDecimal wsp = discountEngine.computeWsp(item).setScale(2, RoundingMode.HALF_UP);
            itemsByWsp.computeIfAbsent(wsp, k -> new ArrayList<>()).add(item);
            itemsByCode.put(item.getItemCode().toUpperCase(), item);
        }

        try (var wb = new HSSFWorkbook(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            int lastRow = sheet.getLastRowNum();

            InvoiceBlock currentBlock = null;

            for (int i = 0; i <= lastRow; i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                String cellA = cellText(row, COL_ITEM_CODE);

                // End of report sentinel — appears once, for the whole file
                if (cellA.toLowerCase().contains("end of") && cellA.toLowerCase().contains("report")) {
                    break;
                }

                // Invoice header anchor
                if (cellA.trim().equalsIgnoreCase("CUS. CODE")) {
                    if (currentBlock != null) {
                        warnings.add("Block at row " + currentBlock.startRow + " (inv: " + currentBlock.invoiceRef
                                + ") replaced by new header before NET value — discarded.");
                    }
                    currentBlock = new InvoiceBlock();
                    currentBlock.startRow = i;
                    parseHeader(sheet, i, currentBlock);
                    continue;
                }

                if (currentBlock == null) continue;

                // Block close — NET Invoice Value row (label lives in col 16, not col 0)
                String subtotalLabel = cellText(row, COL_SUBTOTAL_LABEL);
                if (subtotalLabel.equalsIgnoreCase("NET Invoice Value")) {
                    currentBlock.netInvoiceValue = parseBigDecimal(row, COL_SUBTOTAL_VALUE);
                    Customer customer = resolveCustomer(currentBlock, warnings);
                    long unmatched = currentBlock.lines.stream().filter(l -> l.itemId() == null).count();
                    if (unmatched > 0) {
                        warnings.add("Invoice " + currentBlock.invoiceRef + " — " + unmatched
                                + " line(s) skipped: no " + importCategory + " catalog item matched (wrong category or price/code mismatch).");
                    }
                    invoices.add(toRequest(currentBlock, customer, importCategory));
                    previews.add(toPreview(currentBlock, customer));
                    currentBlock = null;
                    continue;
                }

                // Item line detection — brand-group headers and subtotal rows have no qty here, so they're skipped
                if (isItemLine(row, cellA)) {
                    ParsedLine line = parseItemLine(row, cellA, itemsByWsp, itemsByCode, warnings);
                    if (line != null) {
                        currentBlock.lines.add(line);
                    }
                }
            }

            if (currentBlock != null) {
                warnings.add("Reached end of file with incomplete block (inv: " + currentBlock.invoiceRef
                        + ") — no NET Invoice Value found. Block discarded.");
            }
        }

        return new ParseResult(invoices, previews, warnings);
    }

    // -------- block header parsing --------

    private void parseHeader(Sheet sheet, int rowIdx, InvoiceBlock block) {
        block.customerCode = cellText(sheet.getRow(rowIdx), COL_VALUE_A).trim();
        block.invoiceRef   = cellText(sheet.getRow(rowIdx), COL_VALUE_B).trim();

        Row nameRow = sheet.getRow(rowIdx + 1);
        if (nameRow != null) {
            block.customerHint = cellText(nameRow, COL_VALUE_A).trim();
            block.invoiceDate  = parseExcelDate(nameRow, COL_VALUE_B);
        }
        if (block.invoiceDate == null) block.invoiceDate = LocalDate.now();

        block.invoiceType = detectInvoiceType(sheet, rowIdx);
    }

    private InvoiceType detectInvoiceType(Sheet sheet, int headerRowIdx) {
        // "CASH INVOICE" / "CREDIT INVOICE" sits a couple of rows above the "CUS. CODE" anchor,
        // in col 0 — but isn't always present (e.g. on some repeat pages), so default to CREDIT.
        for (int r = Math.max(0, headerRowIdx - 3); r < headerRowIdx; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            String label = cellText(row, 0).toUpperCase();
            if (label.contains("CASH INVOICE")) return InvoiceType.CASH;
            if (label.contains("CREDIT INVOICE")) return InvoiceType.CREDIT;
        }
        return InvoiceType.CREDIT;
    }

    private LocalDate parseExcelDate(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC) {
            try {
                return DateUtil.getLocalDateTime(cell.getNumericCellValue()).toLocalDate();
            } catch (Exception ignored) { /* not a valid date serial */ }
        }
        return null;
    }

    // -------- line parsing --------

    private boolean isItemLine(Row row, String cellA) {
        if (cellA.isBlank()) return false;
        Cell qtyCell = row.getCell(COL_QTY);
        if (qtyCell == null) return false;
        return qtyCell.getCellType() == CellType.NUMERIC && qtyCell.getNumericCellValue() > 0;
    }

    private ParsedLine parseItemLine(Row row, String itemCode, Map<BigDecimal, List<Item>> itemsByWsp,
                                      Map<String, Item> itemsByCode, List<String> warnings) {
        try {
            int qty = (int) row.getCell(COL_QTY).getNumericCellValue();
            BigDecimal wsp = parseBigDecimal(row, COL_WSP);
            BigDecimal value = parseBigDecimal(row, COL_VALUE);
            BigDecimal mrp = parseBigDecimal(row, COL_MRP);
            String desc = cellText(row, COL_DESCRIPTION);
            String code = itemCode.trim();

            Item item = matchItem(code, wsp, itemsByWsp, itemsByCode, warnings);

            return new ParsedLine(itemCode.trim(), desc, qty, mrp, wsp, value,
                    item != null ? item.getId() : null,
                    item != null ? item.getCategory() : null);
        } catch (Exception e) {
            warnings.add("Skipped row " + row.getRowNum() + " (itemCode=" + itemCode + "): " + e.getMessage());
            return null;
        }
    }

    /**
     * Matches a Ventura row to a catalog item within the selected import category.
     * Price is the primary signal (item codes in the export aren't reliable enough on their own,
     * especially for Stationery) — the row's WSP is looked up against each candidate item's
     * computed WSP (mrp * (1 - marginPct/100)). Item code is only used to disambiguate when
     * several catalog items share the same price, or as a last-resort fallback when no item in
     * the category prices out to match at all.
     */
    private Item matchItem(String code, BigDecimal rowWsp, Map<BigDecimal, List<Item>> itemsByWsp,
                            Map<String, Item> itemsByCode, List<String> warnings) {
        if (rowWsp != null && rowWsp.compareTo(BigDecimal.ZERO) > 0) {
            List<Item> priceMatches = itemsByWsp.get(rowWsp.setScale(2, RoundingMode.HALF_UP));
            if (priceMatches != null && !priceMatches.isEmpty()) {
                if (priceMatches.size() == 1) return priceMatches.get(0);
                Item exact = priceMatches.stream()
                        .filter(it -> it.getItemCode().equalsIgnoreCase(code) || it.getItemCode().equalsIgnoreCase("RC-" + code))
                        .findFirst().orElse(null);
                if (exact != null) return exact;
                Item first = priceMatches.get(0);
                warnings.add("Multiple catalog items share price " + rowWsp + " for code " + code
                        + " — using \"" + first.getItemCode() + "\" (first match); please verify.");
                return first;
            }
        }

        // No catalog item in this category prices out to match — fall back to code matching as
        // a best effort (e.g. Rainco variants stored with an RC- prefix or suffix Ventura doesn't show).
        Item byCode = itemsByCode.get(code.toUpperCase());
        if (byCode == null) byCode = itemsByCode.get(("RC-" + code).toUpperCase());
        if (byCode != null) {
            warnings.add("Item " + code + " matched by code only — its catalog price (" + discountEngine.computeWsp(byCode)
                    + ") did not match the imported price (" + rowWsp + "); please verify.");
            return byCode;
        }

        String prefixKey = ("RC-" + code).toUpperCase();
        Item prefixMatch = itemsByCode.entrySet().stream()
                .filter(e -> e.getKey().startsWith(prefixKey))
                .map(Map.Entry::getValue).findFirst().orElse(null);
        if (prefixMatch != null) {
            warnings.add("Item " + code + " matched by prefix to catalog code " + prefixMatch.getItemCode()
                    + " (\"" + prefixMatch.getDescription() + "\") — price did not match either; please verify this is the correct variant.");
        }
        return prefixMatch;
    }

    private BigDecimal parseBigDecimal(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) return BigDecimal.ZERO;
        if (cell.getCellType() == CellType.NUMERIC) {
            return BigDecimal.valueOf(cell.getNumericCellValue()).setScale(2, java.math.RoundingMode.HALF_UP);
        }
        String s = cell.toString().replaceAll("[^0-9.]", "");
        if (s.isBlank()) return BigDecimal.ZERO;
        try { return new BigDecimal(s); } catch (Exception e) { return BigDecimal.ZERO; }
    }

    private String cellText(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING  -> cell.getStringCellValue().trim();
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                    ? cell.getLocalDateTimeCellValue().toLocalDate().toString()
                    : String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCachedFormulaResultType() == CellType.NUMERIC
                    ? String.valueOf(cell.getNumericCellValue()) : cell.getRichStringCellValue().getString();
            default -> "";
        };
    }

    // -------- DTO assembly --------

    private InvoiceRequest toRequest(InvoiceBlock block, Customer customer, CategoryType importCategory) {
        InvoiceRequest req = new InvoiceRequest();
        // Matching is restricted to importCategory, so every resolved line is guaranteed that category.
        req.setMethod(importCategory == CategoryType.RAINCO ? InvoiceMethod.RAINCO_ONLY : InvoiceMethod.STATIONERY_ONLY);
        req.setInvoiceType(block.invoiceType);
        req.setInvoiceDate(block.invoiceDate);
        req.setExternalRef(block.invoiceRef);
        req.setAgentPrintedNet(block.netInvoiceValue);
        req.setCustomerId(customer != null ? customer.getId() : null);

        List<InvoiceLineRequest> lines = new ArrayList<>();
        for (ParsedLine pl : block.lines) {
            if (pl.itemId != null) {
                InvoiceLineRequest lr = new InvoiceLineRequest();
                lr.setItemId(pl.itemId);
                lr.setQty(pl.qty);
                lines.add(lr);
            }
            // Lines without a catalog match are tracked in warnings by the caller
        }
        req.setLines(lines);
        return req;
    }

    private PreviewInvoice toPreview(InvoiceBlock block, Customer customer) {
        List<PreviewLine> lines = new ArrayList<>();
        for (ParsedLine pl : block.lines) {
            lines.add(new PreviewLine(pl.itemCode, pl.description, pl.qty, pl.wsp, pl.value));
        }
        String customerName = customer != null ? customer.getName()
                : (block.customerHint != null && !block.customerHint.isBlank()
                        ? block.customerHint + " (unresolved)" : null);
        return new PreviewInvoice(block.invoiceRef, customerName, block.invoiceDate, block.netInvoiceValue, lines);
    }

    private Customer resolveCustomer(InvoiceBlock block, List<String> warnings) {
        if (block.customerCode != null && !block.customerCode.isBlank()) {
            var byCode = customerRepo.findByCustomerCode(block.customerCode.trim());
            if (byCode.isPresent()) return byCode.get();
        }
        if (block.customerHint == null || block.customerHint.isBlank()) {
            warnings.add("Invoice " + block.invoiceRef + " — no customer name found near header — customer not resolved.");
            return null;
        }
        String hint = block.customerHint.trim();
        List<Customer> matches = customerRepo.findByNameContainingIgnoreCaseAndActiveTrue(hint);
        if (matches.isEmpty()) {
            warnings.add("Invoice " + block.invoiceRef + " — no customer matching \"" + block.customerHint + "\" (code "
                    + block.customerCode + ") — customer not resolved.");
            return null;
        }
        Customer exact = matches.stream().filter(c -> c.getName().equalsIgnoreCase(hint)).findFirst().orElse(null);
        if (exact != null) return exact;

        if (matches.size() > 1) {
            warnings.add("Invoice " + block.invoiceRef + " — multiple customers matching \"" + block.customerHint
                    + "\" — using first match (" + matches.get(0).getName() + ").");
        }
        return matches.get(0);
    }

    // -------- inner types --------

    private static class InvoiceBlock {
        int startRow;
        String invoiceRef;
        String customerCode;
        String customerHint;
        LocalDate invoiceDate;
        InvoiceType invoiceType = InvoiceType.CREDIT;
        BigDecimal netInvoiceValue;
        List<ParsedLine> lines = new ArrayList<>();
    }

    private record ParsedLine(String itemCode, String description, int qty,
                               BigDecimal mrp, BigDecimal wsp, BigDecimal value,
                               Long itemId, CategoryType category) {}

    public record PreviewLine(String itemCode, String description, int qty,
                               BigDecimal unitPrice, BigDecimal lineTotal) {}

    public record PreviewInvoice(String invoiceNo, String customerName, LocalDate invoiceDate,
                                  BigDecimal netTotal, List<PreviewLine> lines) {}

    public record ParseResult(List<InvoiceRequest> invoices, List<PreviewInvoice> previews, List<String> warnings) {}
}
