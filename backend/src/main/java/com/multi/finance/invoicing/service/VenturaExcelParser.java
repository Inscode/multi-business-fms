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
import java.util.stream.Collectors;

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
    // The free quantity is printed just right of QTY ("12  3" = 12 paid, 3 free). Crystal
    // Reports merges cells, so its exact column drifts — scanned rather than fixed.
    private static final int COL_FREE_FROM   = 24;
    private static final int COL_FREE_TO     = 29;
    private static final int COL_MRP         = 30;
    private static final int COL_WSP         = 46;
    private static final int COL_VALUE       = 55;

    private static final int COL_SUBTOTAL_LABEL = 16;
    private static final int COL_SUBTOTAL_VALUE = 40;

    private final ItemRepository itemRepo;
    private final CustomerRepository customerRepo;
    private final com.multi.finance.invoicing.repository.InvoiceRepository invoiceRepo;
    private final InvoiceNumberService numbering;
    private final DiscountEngineService discountEngine = new DiscountEngineService();

    // open-in-view is off in the FMS — item.getBrand() inside matching needs an open session
    @Transactional(readOnly = true)
    public ParseResult parse(MultipartFile file, CategoryType importCategory) throws IOException {
        List<ParsedInvoice> invoices = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        InvoiceMethod method = importCategory == CategoryType.RAINCO
                ? InvoiceMethod.RAINCO_ONLY : InvoiceMethod.STATIONERY_ONLY;

        // Restrict matching to the selected category so a Rainco import can never pick up a
        // Stationery item (or vice versa) — this is what makes "other category won't be there"
        // hold, without needing to inspect each row's category before it's even matched.
        Map<BigDecimal, List<Item>> itemsByWsp = new HashMap<>();
        Map<String, List<Item>> itemsByDigits = new HashMap<>();
        for (Item item : itemRepo.findByCategoryAndActiveTrue(importCategory)) {
            BigDecimal wsp = discountEngine.computeWsp(item).setScale(2, RoundingMode.HALF_UP);
            itemsByWsp.computeIfAbsent(wsp, k -> new ArrayList<>()).add(item);
            itemsByDigits.computeIfAbsent(digitsOf(item.getItemCode()), k -> new ArrayList<>()).add(item);
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

                    // Any line we could not pin to exactly one catalog item blocks the whole
                    // invoice — importing it partially would understate its value and move
                    // stock for only some of the goods.
                    List<String> unmatchedCodes = currentBlock.lines.stream()
                            .filter(l -> l.itemId() == null)
                            .map(ParsedLine::itemCode)
                            .toList();
                    if (!unmatchedCodes.isEmpty()) {
                        warnings.add("Invoice " + currentBlock.invoiceRef + " BLOCKED — "
                                + unmatchedCodes.size() + " line(s) could not be matched to a single "
                                + importCategory + " item: " + String.join(", ", unmatchedCodes)
                                + ". Fix the catalog (code or price) and re-import.");
                    }

                    // The agent invoice number is what the bill in the bills section is
                    // numbered from, so an invoice without one cannot be collected against
                    // and is refused rather than imported half-usable.
                    boolean missingRef = currentBlock.invoiceRef == null || currentBlock.invoiceRef.isBlank();
                    if (missingRef) {
                        warnings.add("Block at row " + currentBlock.startRow
                                + " has no invoice number in the sheet — it cannot be imported, "
                                + "because the bill raised for it is numbered from that reference.");
                    }

                    // Already loaded from an earlier run of the same file. Caught here so the
                    // accountant sees it in the preview, rather than as a constraint violation
                    // after pressing Import.
                    boolean alreadyImported = !missingRef
                            && invoiceRepo.existsByExternalRefAndMethod(currentBlock.invoiceRef, method);

                    invoices.add(new ParsedInvoice(
                            toRequest(currentBlock, customer, importCategory),
                            toPreview(currentBlock, customer, unmatchedCodes, alreadyImported, missingRef, importCategory),
                            unmatchedCodes, alreadyImported, missingRef));
                    currentBlock = null;
                    continue;
                }

                // Item line detection — brand-group headers and subtotal rows have no qty here, so they're skipped
                if (isItemLine(row, cellA)) {
                    ParsedLine line = parseItemLine(row, cellA, itemsByWsp, itemsByDigits, warnings);
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

        return new ParseResult(invoices, warnings);
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
                                      Map<String, List<Item>> itemsByDigits, List<String> warnings) {
        try {
            int qty = (int) row.getCell(COL_QTY).getNumericCellValue();
            int freeQty = parseFreeQty(row);
            BigDecimal wsp = parseBigDecimal(row, COL_WSP);
            BigDecimal value = parseBigDecimal(row, COL_VALUE);
            BigDecimal mrp = parseBigDecimal(row, COL_MRP);
            String desc = cellText(row, COL_DESCRIPTION);
            String code = itemCode.trim();

            Item item = matchItem(code, wsp, itemsByWsp, itemsByDigits, warnings);

            return new ParsedLine(itemCode.trim(), desc, qty, freeQty, mrp, wsp, value,
                    item != null ? item.getId() : null,
                    item != null ? item.getCategory() : null);
        } catch (Exception e) {
            warnings.add("Skipped row " + row.getRowNum() + " (itemCode=" + itemCode + "): " + e.getMessage());
            return null;
        }
    }

    /**
     * Matches a Ventura row to a catalog item within the selected import category.
     *
     * Price is the primary signal — the row's WSP is looked up against each catalog item's
     * computed WSP. When several items share that price, they are told apart by the digits
     * in the item code: the export prints bare numbers (e.g. 4321) while the catalog may
     * carry a prefix (RC-4321), so only the digits are compared, with leading zeros ignored.
     *
     * If no item prices out, the digits alone are used as a last resort — flagged as a
     * warning, because a price mismatch means one of the two sides is out of date.
     *
     * Anything ambiguous returns no match rather than guessing: a wrong pick would deduct
     * stock from the wrong item, so the invoice is blocked and a human decides.
     */
    private Item matchItem(String code, BigDecimal rowWsp, Map<BigDecimal, List<Item>> itemsByWsp,
                            Map<String, List<Item>> itemsByDigits, List<String> warnings) {
        String digits = digitsOf(code);

        // ── 1. Price ────────────────────────────────────────────────────────
        if (rowWsp != null && rowWsp.compareTo(BigDecimal.ZERO) > 0) {
            List<Item> priceMatches = itemsByWsp.get(rowWsp.setScale(2, RoundingMode.HALF_UP));

            if (priceMatches != null && !priceMatches.isEmpty()) {
                if (priceMatches.size() == 1) return priceMatches.get(0);

                // ── 2. Same price — separate them by the digits in the code ──
                List<Item> digitMatches = digits.isEmpty() ? List.of() : priceMatches.stream()
                        .filter(it -> digits.equals(digitsOf(it.getItemCode())))
                        .toList();

                if (digitMatches.size() == 1) return digitMatches.get(0);

                if (digitMatches.size() > 1) {
                    warnings.add("Code " + code + " at price " + rowWsp + " matches "
                            + digitMatches.size() + " catalog items with the same number ("
                            + codeList(digitMatches) + ") — cannot tell them apart.");
                    return null;
                }

                warnings.add("Code " + code + " — " + priceMatches.size()
                        + " catalog items share price " + rowWsp + " (" + codeList(priceMatches)
                        + ") and none carries that number, so the right one is unknown.");
                return null;
            }
        }

        // ── 3. No price match — fall back to the code's digits ──────────────
        if (!digits.isEmpty()) {
            List<Item> byDigits = itemsByDigits.getOrDefault(digits, List.of());
            if (byDigits.size() == 1) {
                Item item = byDigits.get(0);
                warnings.add("Item " + code + " matched by code number only — catalog price ("
                        + discountEngine.computeWsp(item) + ") does not match the imported price ("
                        + rowWsp + "); please verify.");
                return item;
            }
            if (byDigits.size() > 1) {
                warnings.add("Item " + code + " — code number matches " + byDigits.size()
                        + " catalog items (" + codeList(byDigits)
                        + ") and the price matched none, so the right one is unknown.");
                return null;
            }
        }

        return null;
    }

    /**
     * The comparable part of an item code: digits only, leading zeros dropped.
     * "RC-4321" and "4321" both reduce to 4321.
     */
    private String digitsOf(String code) {
        if (code == null) return "";
        String digits = code.replaceAll("[^0-9]", "");
        return digits.replaceFirst("^0+(?=[0-9])", "");
    }

    private String codeList(List<Item> items) {
        return items.stream().map(Item::getItemCode).collect(Collectors.joining(", "));
    }

    /**
     * The free quantity sitting between QTY and MRP. Only whole positive numbers count —
     * MRP and the money columns are decimals, so they can't be mistaken for one.
     */
    private int parseFreeQty(Row row) {
        for (int c = COL_FREE_FROM; c <= COL_FREE_TO; c++) {
            Cell cell = row.getCell(c);
            if (cell == null || cell.getCellType() != CellType.NUMERIC) continue;
            double v = cell.getNumericCellValue();
            if (v > 0 && v == Math.floor(v)) return (int) v;
        }
        return 0;
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
        req.setBilledName(block.customerHint);
        req.setOriginalCustomerName(customer != null ? customer.getName() : null);

        // Rainco book numbers run below 10,000 and arrive through the same import as its
        // system bills, so the reference itself decides which kind of bill this is.
        var business = numbering.businessFor(req.getMethod());
        req.setBillSource(numbering.sourceForImport(business, block.invoiceRef));
        req.setBillNumber(numbering.digitsOf(block.invoiceRef));

        List<InvoiceLineRequest> lines = new ArrayList<>();
        for (ParsedLine pl : block.lines) {
            if (pl.itemId != null) {
                InvoiceLineRequest lr = new InvoiceLineRequest();
                lr.setItemId(pl.itemId);
                lr.setQty(pl.qty);
                lr.setFreeQty(pl.freeQty);
                lines.add(lr);
            }
            // Lines without a catalog match are tracked in warnings by the caller
        }
        req.setLines(lines);
        return req;
    }

    private PreviewInvoice toPreview(InvoiceBlock block, Customer customer,
                                      List<String> unmatchedCodes, boolean alreadyImported,
                                      boolean missingRef, CategoryType importCategory) {
        List<PreviewLine> lines = new ArrayList<>();
        for (ParsedLine pl : block.lines) {
            lines.add(new PreviewLine(pl.itemCode, pl.description, pl.qty, pl.freeQty, pl.wsp, pl.value));
        }
        // An unmatched item is a hard block — nobody can fix it from this screen. An
        // unresolved customer is not: the accountant picks one in the preview, which is
        // the same control they use to redirect an invoice billed under another name.
        String blockReason = null;
        if (missingRef) {
            blockReason = "No invoice number on this block — nothing to number the bill from";
        } else if (!unmatchedCodes.isEmpty()) {
            blockReason = unmatchedCodes.size() + " item(s) not matched: " + String.join(", ", unmatchedCodes);
        }

        String billNumber = null;
        if (!missingRef) {
            var business = numbering.businessFor(importCategory == CategoryType.RAINCO
                    ? InvoiceMethod.RAINCO_ONLY : InvoiceMethod.STATIONERY_ONLY);
            billNumber = numbering.format(business,
                    numbering.sourceForImport(business, block.invoiceRef),
                    numbering.digitsOf(block.invoiceRef));
        }

        return new PreviewInvoice(block.invoiceRef, billNumber,
                customer != null ? customer.getId() : null,
                customer != null ? customer.getName() : null,
                block.customerHint,
                block.invoiceDate,
                block.netInvoiceValue, lines, unmatchedCodes,
                blockReason != null, blockReason, alreadyImported);
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

    private record ParsedLine(String itemCode, String description, int qty, int freeQty,
                               BigDecimal mrp, BigDecimal wsp, BigDecimal value,
                               Long itemId, CategoryType category) {}

    public record PreviewLine(String itemCode, String description, int qty, int freeQty,
                               BigDecimal unitPrice, BigDecimal lineTotal) {}

    /**
     * @param customerId   resolved customer, or null when the sheet's name matched nothing
     * @param customerName the resolved customer's own name
     * @param billedName   the name printed on the source invoice — often not the real customer
     */
    /** @param billNumber the number this will carry in both the invoicing and bills sections */
    public record PreviewInvoice(String invoiceNo, String billNumber,
                                  Long customerId, String customerName,
                                  String billedName, LocalDate invoiceDate,
                                  BigDecimal netTotal, List<PreviewLine> lines,
                                  List<String> unmatchedCodes, boolean blocked, String blockReason,
                                  boolean alreadyImported) {}

    /** An invoice block plus everything needed to decide whether it may be imported. */
    public record ParsedInvoice(InvoiceRequest request, PreviewInvoice preview,
                                 List<String> unmatchedCodes, boolean alreadyImported,
                                 boolean missingRef) {
        public boolean blocked() { return missingRef || !unmatchedCodes.isEmpty(); }
    }

    public record ParseResult(List<ParsedInvoice> invoices, List<String> warnings) {
        public List<PreviewInvoice> previews() {
            return invoices.stream().map(ParsedInvoice::preview).toList();
        }
    }
}
