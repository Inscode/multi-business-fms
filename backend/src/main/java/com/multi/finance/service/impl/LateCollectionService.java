package com.multi.finance.service.impl;

import com.multi.finance.dto.response.LateCollectionReport;
import com.multi.finance.entity.Bill;
import com.multi.finance.entity.Payment;
import com.multi.finance.enums.BusinessType;
import com.multi.finance.enums.PaymentStatus;
import com.multi.finance.repository.PaymentRepository;
import com.multi.finance.service.CreditTerms;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What the money that came in was worth waiting for.
 *
 * <p>The company is given 70 days to pay the principal. Anything collected after that was
 * funded out of its own cash in the meantime, and nothing else in the system records
 * that: the bill closes, the balance goes to zero, and the fact that it closed twenty
 * days too late leaves no trace. This puts that back.
 *
 * <p>Read-only and computed per request, keyed on the date each payment landed so the
 * period can be reconciled against what actually reached the bank.
 */
@Service
@RequiredArgsConstructor
public class LateCollectionService {

    private final PaymentRepository paymentRepository;

    /**
     * Everything collected between two dates, banded by how old the bill was when the
     * money arrived.
     *
     * @param business narrow to one business, or null for all three together
     * @param from     first payment date counted
     * @param to       last payment date counted
     */
    @Transactional(readOnly = true)
    public LateCollectionReport build(BusinessType business, LocalDate from, LocalDate to) {
        LocalDate start = from != null ? from : LocalDate.now().withDayOfMonth(1);
        LocalDate end = to != null ? to : LocalDate.now();

        // Confirmed only. An entered-but-unconfirmed payment may yet be rejected or
        // bounce, and counting it would report money as collected that the bank has not
        // agreed to.
        List<Payment> payments = paymentRepository
                .findByStatusWithBillBetween(PaymentStatus.CONFIRMED, start, end).stream()
                .filter(p -> p.getBill() != null)
                .filter(p -> business == null || p.getBill().getBusiness() == business)
                .filter(p -> p.getBill().getBillDate() != null && p.getPaymentDate() != null)
                // A payment dated before its bill is a data-entry slip, not a fast
                // collection; a negative age would flatter the average.
                .filter(p -> !p.getPaymentDate().isBefore(p.getBill().getBillDate()))
                .toList();

        List<LateCollectionReport.PaymentRow> rows = new ArrayList<>();
        Map<String, Agg> byBand = new LinkedHashMap<>();
        Map<String, CustAgg> byCustomer = new LinkedHashMap<>();

        BigDecimal total = BigDecimal.ZERO;
        BigDecimal weightedDays = BigDecimal.ZERO;
        BigDecimal pastDanger = BigDecimal.ZERO;
        BigDecimal beyond = BigDecimal.ZERO;

        for (Payment p : payments) {
            Bill bill = p.getBill();
            int days = (int) ChronoUnit.DAYS.between(bill.getBillDate(), p.getPaymentDate());
            BigDecimal amount = p.getAmount() == null ? BigDecimal.ZERO : p.getAmount();
            // Judged on its own terms. A cash bill is due when the goods are handed
            // over, so measuring it against the credit run reported a cash sale
            // collected three weeks late as comfortably on time.
            boolean cash = bill.getBillType() == com.multi.finance.enums.BillType.CASH;
            String band = CreditTerms.bandFor(days, cash);

            total = total.add(amount);
            weightedDays = weightedDays.add(amount.multiply(BigDecimal.valueOf(days)));
            if (days > CreditTerms.dangerDays(cash)) pastDanger = pastDanger.add(amount);
            if (days > CreditTerms.supplierDays(cash)) beyond = beyond.add(amount);

            byBand.computeIfAbsent(band, k -> new Agg()).add(amount);

            String key = bill.getCustomer() != null
                    ? "#" + bill.getCustomer().getId()
                    : "n:" + String.valueOf(bill.getCustomerName()).trim().toUpperCase();
            CustAgg ca = byCustomer.computeIfAbsent(key, k -> new CustAgg());
            ca.customerId = bill.getCustomer() != null ? bill.getCustomer().getId() : null;
            ca.name = bill.getCustomerName();
            ca.area = bill.getArea();
            ca.add(amount, days, cash);

            rows.add(LateCollectionReport.PaymentRow.builder()
                    .billId(bill.getId())
                    .billNumber(bill.getBillNumber())
                    .customerName(bill.getCustomerName())
                    .area(bill.getArea())
                    .billDate(bill.getBillDate())
                    .paymentDate(p.getPaymentDate())
                    .days(days)
                    .band(band)
                    .amount(amount)
                    .paymentType(p.getPaymentType() == null ? null : p.getPaymentType().name())
                    .billType(bill.getBillType() == null ? null : bill.getBillType().name())
                    .build());
        }

        // Oldest money first — the whole point of the list is what took too long.
        rows.sort(Comparator.comparingInt(LateCollectionReport.PaymentRow::getDays).reversed());

        List<LateCollectionReport.Band> bands = new ArrayList<>();
        for (String band : List.of("ON_TIME", "WATCH", "LATE", "BEYOND_TERMS")) {
            Agg a = byBand.getOrDefault(band, new Agg());
            bands.add(LateCollectionReport.Band.builder()
                    .band(band)
                    .label(labelFor(band))
                    .amount(a.amount)
                    .count(a.count)
                    .pct(share(a.amount, total))
                    .build());
        }

        List<LateCollectionReport.CustomerRow> customers = byCustomer.values().stream()
                .map(c -> LateCollectionReport.CustomerRow.builder()
                        .customerId(c.customerId)
                        .customerName(c.name)
                        .area(c.area)
                        .collected(c.amount)
                        // Disjoint: the late window, then everything past it.
                        .lateAmount(c.pastDanger.subtract(c.beyond))
                        .beyondTermsAmount(c.beyond)
                        .pastDangerAmount(c.pastDanger)
                        .avgDaysWeighted(c.weightedAverage())
                        .worstDays(c.worstDays)
                        .paymentCount(c.count)
                        .build())
                // Ranked by what was collected beyond terms, then past the danger line.
                // Sorting by total collected would put the biggest customer on top
                // whether or not they were ever late, which is the wrong list.
                .sorted(Comparator
                        .comparing((LateCollectionReport.CustomerRow r) -> r.getBeyondTermsAmount()).reversed()
                        .thenComparing(Comparator.comparing(
                                LateCollectionReport.CustomerRow::getPastDangerAmount).reversed()))
                .toList();

        return LateCollectionReport.builder()
                .business(business == null ? "ALL" : business.name())
                .from(start)
                .to(end)
                .sealDays(CreditTerms.SEAL_DAYS)
                .dangerDays(CreditTerms.DANGER_DAYS)
                .supplierDays(CreditTerms.SUPPLIER_DAYS)
                .cashSealDays(CreditTerms.CASH_SEAL_DAYS)
                .cashDangerDays(CreditTerms.CASH_DANGER_DAYS)
                .cashSupplierDays(CreditTerms.CASH_SUPPLIER_DAYS)
                .totalCollected(total)
                .paymentCount(payments.size())
                .avgDaysWeighted(total.compareTo(BigDecimal.ZERO) > 0
                        ? weightedDays.divide(total, 0, RoundingMode.HALF_UP).intValue()
                        : null)
                .beyondTermsAmount(beyond)
                .beyondTermsPct(share(beyond, total))
                .pastDangerAmount(pastDanger)
                .pastDangerPct(share(pastDanger, total))
                .bands(bands)
                .customers(customers)
                .payments(rows)
                .build();
    }

    /**
     * What each band means, in words rather than day-counts.
     *
     * <p>The numbers differ by bill type — cash is due on delivery, credit runs to 70
     * days — so a single "46–60 days" label would be wrong for half the rows it covered.
     */
    private static String labelFor(String band) {
        return switch (band) {
            case "ON_TIME"      -> "Within terms";
            case "WATCH"        -> "Past the bill's terms";
            case "LATE"         -> "Past the danger line";
            case "BEYOND_TERMS" -> "Past what we owe the supplier by";
            default             -> band;
        };
    }

    private static BigDecimal share(BigDecimal part, BigDecimal whole) {
        if (whole == null || whole.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;
        return part.multiply(new BigDecimal("100")).divide(whole, 1, RoundingMode.HALF_UP);
    }

    private static final class Agg {
        BigDecimal amount = BigDecimal.ZERO;
        int count;
        void add(BigDecimal a) { amount = amount.add(a); count++; }
    }

    private static final class CustAgg {
        Long customerId;
        String name;
        String area;
        BigDecimal amount = BigDecimal.ZERO;
        BigDecimal pastDanger = BigDecimal.ZERO;
        BigDecimal beyond = BigDecimal.ZERO;
        BigDecimal weighted = BigDecimal.ZERO;
        int worstDays;
        int count;

        void add(BigDecimal a, int days, boolean cash) {
            amount = amount.add(a);
            weighted = weighted.add(a.multiply(BigDecimal.valueOf(days)));
            if (days > CreditTerms.dangerDays(cash)) pastDanger = pastDanger.add(a);
            if (days > CreditTerms.supplierDays(cash)) beyond = beyond.add(a);
            if (days > worstDays) worstDays = days;
            count++;
        }

        Integer weightedAverage() {
            return amount.compareTo(BigDecimal.ZERO) > 0
                    ? weighted.divide(amount, 0, RoundingMode.HALF_UP).intValue()
                    : null;
        }
    }
}
