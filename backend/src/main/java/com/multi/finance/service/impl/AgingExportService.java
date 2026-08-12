package com.multi.finance.service.impl;

import java.util.Set;
import java.util.Arrays;
import com.multi.finance.dto.response.AgingCustomerEntry;
import com.multi.finance.dto.response.AgingExportBillRow;
import com.multi.finance.dto.response.AgingExportResponse;
import com.multi.finance.entity.Bill;
import com.multi.finance.enums.BillStatus;
import com.multi.finance.enums.BillType;
import com.multi.finance.enums.BusinessType;
import com.multi.finance.repository.BillRepository;
import com.multi.finance.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Printable / downloadable aging report, scoped to a business and optionally an area
 * and bill type. Cash and credit are reported separately because they age on different
 * scales — credit in 30-day bands, cash in days.
 */
@Service
@RequiredArgsConstructor
public class AgingExportService {

    /** How the customer summary is ordered on the printed sheet. */
    public enum SortMode {
        /** Longest-waiting first — the customer whose oldest bill goes back furthest. */
        AGE,
        /** Largest balance first. */
        AMOUNT
    }

    private static final int OVERDUE_DAYS = 45;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private final BillRepository billRepository;
    private final PaymentRepository paymentRepository;

    @Transactional(readOnly = true)
    public AgingExportResponse getExport(BusinessType business, String area, BillType billType) {
        return getExport(business, area, billType, SortMode.AGE);
    }

    @Transactional(readOnly = true)
    public AgingExportResponse getExport(BusinessType business, String area,
                                         BillType billType, SortMode sort) {
        return getExport(business, splitAreas(area), billType, sort);
    }

    /**
     * @param areas the areas to report on; empty or null means every area. Several can
     *              be given at once, because a rep's round rarely maps onto one area
     *              and printing a sheet per area to staple together helps nobody.
     */
    @Transactional(readOnly = true)
    public AgingExportResponse getExport(BusinessType business, List<String> areas,
                                         BillType billType, SortMode sort) {
        LocalDate today = LocalDate.now();
        List<BillStatus> excluded = List.of(
                BillStatus.COMPLETED, BillStatus.AWAITING_CONFIRMATION, BillStatus.CANCELLED);

        Set<String> wanted = areas == null ? Set.of()
                : areas.stream().filter(a -> a != null && !a.isBlank())
                       .map(a -> a.trim().toUpperCase()).collect(Collectors.toSet());

        List<Bill> inScope = billRepository
                .findByBusinessAndStatusNotInOrderByCreatedAtDesc(business, excluded).stream()
                .filter(b -> b.getBalanceRemaining() != null
                        && b.getBalanceRemaining().compareTo(BigDecimal.ZERO) > 0
                        && !Boolean.TRUE.equals(b.getWillBeLinked()))
                // A return credit can settle a bill without moving it to COMPLETED,
                // so the flag is checked as well as the status.
                .filter(b -> !Boolean.TRUE.equals(b.getFullyPaid()))
                .filter(b -> wanted.isEmpty()
                        || (b.getArea() != null && wanted.contains(b.getArea().trim().toUpperCase())))
                .filter(b -> billType == null || b.getBillType() == billType)
                .toList();

        // Hidden by an admin as not chaseable. Counted and reported rather than dropped
        // without trace — a number quietly missing from a report is worse than one
        // shown and explained.
        List<Bill> hidden = inScope.stream()
                .filter(b -> Boolean.TRUE.equals(b.getExcludedFromAging()))
                .toList();
        BigDecimal hiddenAmount = hidden.stream()
                .map(Bill::getBalanceRemaining)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Bill> bills = inScope.stream()
                .filter(b -> !Boolean.TRUE.equals(b.getExcludedFromAging()))
                .toList();

        Map<Long, LocalDate> lastPayment = lastPaymentDates(bills);

        List<AgingExportBillRow> billRows = bills.stream()
                .map(b -> toBillRow(b, today, lastPayment.get(b.getId())))
                .sorted(Comparator.comparing(AgingExportBillRow::getArea, Comparator.nullsLast(String::compareTo))
                        .thenComparing(AgingExportBillRow::getBillDate))
                .toList();

        List<AgingCustomerEntry> creditCustomers = summarise(
                bills.stream().filter(b -> b.getBillType() != BillType.CASH).toList(), today, lastPayment);
        List<AgingCustomerEntry> cashCustomers = summarise(
                bills.stream().filter(b -> b.getBillType() == BillType.CASH).toList(), today, lastPayment);

        // The chosen order applies to both sections so the sheet reads consistently
        applySort(creditCustomers, sort);
        applySort(cashCustomers, sort);

        BigDecimal totalCredit = sum(creditCustomers, AgingCustomerEntry::getTotalOutstanding);
        BigDecimal totalCash = sum(cashCustomers, AgingCustomerEntry::getTotalOutstanding);

        Set<String> distinctCustomers = new HashSet<>();
        creditCustomers.forEach(c -> distinctCustomers.add(c.getCustomerName()));
        cashCustomers.forEach(c -> distinctCustomers.add(c.getCustomerName()));

        return AgingExportResponse.builder()
                .business(business.name())
                .area(wanted.isEmpty() ? null : String.join(", ", sortedAreas(wanted)))
                .excludedCount(hidden.size())
                .excludedAmount(hiddenAmount)
                .billType(billType != null ? billType.name() : null)
                .generatedOn(today)
                .creditCustomers(creditCustomers)
                .cashCustomers(cashCustomers)
                .bills(billRows)
                .totalCredit(totalCredit)
                .totalCash(totalCash)
                .totalOutstanding(totalCredit.add(totalCash))
                .customerCount(distinctCustomers.size())
                .billCount(billRows.size())
                .build();
    }

    /** Accepts one area, or several separated by commas. */
    private List<String> splitAreas(String area) {
        if (area == null || area.isBlank()) return List.of();
        return Arrays.stream(area.split(",")).map(String::trim)
                .filter(a -> !a.isEmpty()).toList();
    }

    private List<String> sortedAreas(Set<String> areas) {
        return areas.stream().sorted().toList();
    }

    // ── Excel ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public byte[] getExcel(BusinessType business, String area, BillType billType, SortMode sort) throws IOException {
        AgingExportResponse data = getExport(business, area, billType, sort);

        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle title = boldStyle(wb, 13);
            CellStyle header = headerStyle(wb);
            CellStyle money = moneyStyle(wb);
            CellStyle moneyBold = moneyStyle(wb);
            moneyBold.setFont(boldFont(wb, 11));

            Sheet summary = wb.createSheet("Summary");
            int r = 0;
            r = writeTitleBlock(summary, r, data, title);

            if (!data.getCreditCustomers().isEmpty()) {
                r = writeCustomerSection(summary, r, "CREDIT BILLS", data.getCreditCustomers(),
                        new String[]{"Customer", "Area", "Bills", "Oldest Bill", "Last Payment",
                                     "0-30", "31-60", "61-90", "91+", "Overdue 45+", "Total"},
                        c -> new BigDecimal[]{c.getCurrent(), c.getDays31to60(), c.getDays61to90(),
                                              c.getDays91plus(), c.getOverdue(), c.getTotalOutstanding()},
                        header, money, moneyBold, title);
            }

            if (!data.getCashCustomers().isEmpty()) {
                r = writeCustomerSection(summary, r, "CASH BILLS", data.getCashCustomers(),
                        new String[]{"Customer", "Area", "Bills", "Oldest Bill", "Last Payment",
                                     "Follow-up 1-7", "Urgent 8-14", "Serious 15+", "Cash Pending"},
                        c -> new BigDecimal[]{c.getCashFollowUp(), c.getCashUrgent(),
                                              c.getCashSerious(), c.getCashPending()},
                        header, money, moneyBold, title);
            }

            Row grand = summary.createRow(r + 1);
            grand.createCell(0).setCellValue("GRAND TOTAL OUTSTANDING");
            grand.getCell(0).setCellStyle(boldStyle(wb, 11));
            Cell grandVal = grand.createCell(1);
            grandVal.setCellValue(data.getTotalOutstanding().doubleValue());
            grandVal.setCellStyle(moneyBold);

            for (int i = 0; i < 11; i++) summary.autoSizeColumn(i);

            writeBillsSheet(wb, data, header, money);

            wb.write(out);
            return out.toByteArray();
        }
    }

    private int writeTitleBlock(Sheet sheet, int r, AgingExportResponse d, CellStyle title) {
        Row t = sheet.createRow(r++);
        t.createCell(0).setCellValue("AGING REPORT — " + d.getBusiness());
        t.getCell(0).setCellStyle(title);

        Row scope = sheet.createRow(r++);
        scope.createCell(0).setCellValue("Area: " + (d.getArea() != null ? d.getArea() : "All areas")
                + "   |   Type: " + (d.getBillType() != null ? d.getBillType() : "Cash + Credit")
                + "   |   Generated: " + d.getGeneratedOn().format(DATE_FMT));
        return r + 1;
    }

    private int writeCustomerSection(Sheet sheet, int r, String heading,
                                     List<AgingCustomerEntry> rows, String[] headers,
                                     Function<AgingCustomerEntry, BigDecimal[]> amounts,
                                     CellStyle header, CellStyle money, CellStyle moneyBold,
                                     CellStyle title) {
        Row h = sheet.createRow(r++);
        h.createCell(0).setCellValue(heading);
        h.getCell(0).setCellStyle(title);

        Row hr = sheet.createRow(r++);
        for (int i = 0; i < headers.length; i++) {
            Cell c = hr.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(header);
        }

        int amountCols = amounts.apply(rows.get(0)).length;
        BigDecimal[] totals = new BigDecimal[amountCols];
        Arrays.fill(totals, BigDecimal.ZERO);

        for (AgingCustomerEntry e : rows) {
            Row row = sheet.createRow(r++);
            row.createCell(0).setCellValue(e.getCustomerName());
            row.createCell(1).setCellValue(e.getArea() != null ? e.getArea() : "—");
            row.createCell(2).setCellValue(e.getBillCount());
            row.createCell(3).setCellValue(e.getOldestBillDate() != null
                    ? e.getOldestBillDate().format(DATE_FMT) : "—");
            row.createCell(4).setCellValue(e.getLastPaymentDate() != null
                    ? e.getLastPaymentDate().format(DATE_FMT) : "Never");

            BigDecimal[] vals = amounts.apply(e);
            for (int i = 0; i < vals.length; i++) {
                Cell c = row.createCell(5 + i);
                c.setCellValue(nz(vals[i]).doubleValue());
                c.setCellStyle(money);
                totals[i] = totals[i].add(nz(vals[i]));
            }
        }

        Row totalRow = sheet.createRow(r++);
        totalRow.createCell(0).setCellValue("TOTAL");
        totalRow.getCell(0).setCellStyle(header);
        for (int i = 0; i < totals.length; i++) {
            Cell c = totalRow.createCell(5 + i);
            c.setCellValue(totals[i].doubleValue());
            c.setCellStyle(moneyBold);
        }
        return r + 1;
    }

    private void writeBillsSheet(Workbook wb, AgingExportResponse d, CellStyle header, CellStyle money) {
        Sheet sheet = wb.createSheet("Bills");
        String[] headers = {"Bill No.", "Bill Date", "Days", "Customer", "Area", "Type",
                            "Bill Total", "Paid", "Balance", "Bucket", "With", "Last Payment"};
        Row hr = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell c = hr.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(header);
        }

        int r = 1;
        for (AgingExportBillRow b : d.getBills()) {
            Row row = sheet.createRow(r++);
            row.createCell(0).setCellValue(b.getBillNumber());
            row.createCell(1).setCellValue(b.getBillDate() != null ? b.getBillDate().format(DATE_FMT) : "");
            row.createCell(2).setCellValue(b.getAgeDays());
            row.createCell(3).setCellValue(b.getCustomerName());
            row.createCell(4).setCellValue(b.getArea() != null ? b.getArea() : "—");
            row.createCell(5).setCellValue(b.getBillType());
            cellMoney(row, 6, b.getTotalAmount(), money);
            cellMoney(row, 7, b.getAmountPaid(), money);
            cellMoney(row, 8, b.getBalance(), money);
            row.createCell(9).setCellValue(b.getBucket());
            row.createCell(10).setCellValue(b.getWorkerName() != null ? b.getWorkerName() : "—");
            row.createCell(11).setCellValue(b.getLastPaymentDate() != null
                    ? b.getLastPaymentDate().format(DATE_FMT) : "Never");
        }
        for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);
    }

    // ── helpers ──────────────────────────────────────────────────────

    private Map<Long, LocalDate> lastPaymentDates(List<Bill> bills) {
        List<Long> ids = bills.stream().map(Bill::getId).toList();
        Map<Long, LocalDate> map = new HashMap<>();
        if (ids.isEmpty()) return map;
        paymentRepository.findLastConfirmedDatesByBillIds(ids).forEach(row ->
                map.put((Long) row[0], (LocalDate) row[1]));
        return map;
    }

    private AgingExportBillRow toBillRow(Bill b, LocalDate today, LocalDate lastPmt) {
        LocalDate billDate = b.getBillDate() != null ? b.getBillDate() : b.getCreatedAt().toLocalDate();
        long age = ChronoUnit.DAYS.between(billDate, today);
        boolean cash = b.getBillType() == BillType.CASH;

        return AgingExportBillRow.builder()
                .billNumber(b.getBillNumber())
                .billDate(billDate)
                .ageDays(age)
                .customerName(b.getCustomerName())
                .area(b.getArea())
                .billType(b.getBillType() != null ? b.getBillType().name() : "CREDIT")
                .totalAmount(b.getTotalAmount())
                .amountPaid(b.getAmountPaid())
                .balance(b.getBalanceRemaining())
                .bucket(cash ? cashBucket(age) : creditBucket(age))
                .workerName(b.getCurrentHolder() != null ? b.getCurrentHolder().getFullName() : null)
                .lastPaymentDate(lastPmt)
                .build();
    }

    private String creditBucket(long age) {
        if (age <= 30) return "0-30";
        if (age <= 60) return "31-60";
        if (age <= 90) return "61-90";
        return "91+";
    }

    private String cashBucket(long age) {
        if (age == 0) return "Today";
        if (age <= 7) return "1-7";
        if (age <= 14) return "8-14";
        return "15+";
    }

    /** Groups bills by customer and fills the aging buckets — same rules as the on-screen report. */
    private List<AgingCustomerEntry> summarise(List<Bill> bills, LocalDate today,
                                               Map<Long, LocalDate> lastPayment) {
        record CKey(String name, Long id, String area) {}

        Map<CKey, List<Bill>> byCustomer = bills.stream().collect(Collectors.groupingBy(b ->
                new CKey(b.getCustomerName(),
                        b.getCustomer() != null ? b.getCustomer().getId() : null,
                        b.getArea() != null ? b.getArea() : "")));

        List<AgingCustomerEntry> entries = new ArrayList<>();
        for (Map.Entry<CKey, List<Bill>> e : byCustomer.entrySet()) {
            BigDecimal total = BigDecimal.ZERO, overdue = BigDecimal.ZERO, cur = BigDecimal.ZERO;
            BigDecimal d3160 = BigDecimal.ZERO, d6190 = BigDecimal.ZERO, d91 = BigDecimal.ZERO;
            BigDecimal cashPending = BigDecimal.ZERO, cashFollowUp = BigDecimal.ZERO;
            BigDecimal cashUrgent = BigDecimal.ZERO, cashSerious = BigDecimal.ZERO;
            LocalDate oldest = today, lastPmt = null;

            for (Bill b : e.getValue()) {
                BigDecimal bal = b.getBalanceRemaining();
                LocalDate d = b.getBillDate() != null ? b.getBillDate() : b.getCreatedAt().toLocalDate();
                long age = ChronoUnit.DAYS.between(d, today);

                total = total.add(bal);
                if (d.isBefore(oldest)) oldest = d;

                LocalDate pmt = lastPayment.get(b.getId());
                if (pmt != null && (lastPmt == null || pmt.isAfter(lastPmt))) lastPmt = pmt;

                if (b.getBillType() == BillType.CASH) {
                    cashPending = cashPending.add(bal);
                    if (age == 0)        { /* today — not yet chaseable */ }
                    else if (age <= 7)   cashFollowUp = cashFollowUp.add(bal);
                    else if (age <= 14)  cashUrgent = cashUrgent.add(bal);
                    else                 cashSerious = cashSerious.add(bal);
                } else {
                    if (age >= OVERDUE_DAYS) overdue = overdue.add(bal);
                    if (age <= 30)      cur = cur.add(bal);
                    else if (age <= 60) d3160 = d3160.add(bal);
                    else if (age <= 90) d6190 = d6190.add(bal);
                    else                d91 = d91.add(bal);
                }
            }

            entries.add(AgingCustomerEntry.builder()
                    .customerName(e.getKey().name())
                    .customerId(e.getKey().id())
                    .area(e.getKey().area().isBlank() ? null : e.getKey().area())
                    .totalOutstanding(total)
                    .overdue(overdue)
                    .current(cur)
                    .days31to60(d3160)
                    .days61to90(d6190)
                    .days91plus(d91)
                    .cashPending(cashPending)
                    .cashFollowUp(cashFollowUp)
                    .cashUrgent(cashUrgent)
                    .cashSerious(cashSerious)
                    .billCount(e.getValue().size())
                    .oldestBillDate(oldest)
                    .lastPaymentDate(lastPmt)
                    .build());
        }

        entries.sort(Comparator.comparing(AgingCustomerEntry::getArea, Comparator.nullsLast(String::compareTo))
                .thenComparing(Comparator.comparing(AgingCustomerEntry::getTotalOutstanding).reversed()));
        return entries;
    }

    /**
     * Orders the summary. AGE puts the longest-waiting customer first (oldest bill date
     * ascending); AMOUNT puts the largest balance first. Area is only a tie-breaker —
     * the chosen key leads, so the top of the page is what needs attention.
     */
    private void applySort(List<AgingCustomerEntry> entries, SortMode sort) {
        Comparator<AgingCustomerEntry> comparator = sort == SortMode.AMOUNT
                ? Comparator.comparing(AgingCustomerEntry::getTotalOutstanding,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                : Comparator.comparing(AgingCustomerEntry::getOldestBillDate,
                        Comparator.nullsLast(Comparator.naturalOrder()));

        entries.sort(comparator
                .thenComparing(AgingCustomerEntry::getArea, Comparator.nullsLast(String::compareTo))
                .thenComparing(AgingCustomerEntry::getCustomerName, Comparator.nullsLast(String::compareTo)));
    }

    private BigDecimal sum(List<AgingCustomerEntry> list, Function<AgingCustomerEntry, BigDecimal> f) {
        return list.stream().map(f).map(this::nz).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal nz(BigDecimal v) { return v != null ? v : BigDecimal.ZERO; }

    private void cellMoney(Row row, int col, BigDecimal value, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(nz(value).doubleValue());
        c.setCellStyle(style);
    }

    private Font boldFont(Workbook wb, int size) {
        Font f = wb.createFont();
        f.setBold(true);
        f.setFontHeightInPoints((short) size);
        return f;
    }

    private CellStyle boldStyle(Workbook wb, int size) {
        CellStyle s = wb.createCellStyle();
        s.setFont(boldFont(wb, size));
        return s;
    }

    private CellStyle headerStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        s.setFont(boldFont(wb, 11));
        s.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return s;
    }

    private CellStyle moneyStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        s.setDataFormat(wb.createDataFormat().getFormat("#,##0.00"));
        return s;
    }
}
