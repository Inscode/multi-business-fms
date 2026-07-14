package com.multi.finance.service.impl;

import com.multi.finance.dto.response.*;
import com.multi.finance.entity.*;
import com.multi.finance.enums.*;
import com.multi.finance.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CopilotService {

    private static final List<BillStatus> OUTSTANDING_EXCLUDED =
            List.of(BillStatus.CANCELLED);

    private static final int CREDIT_OVERDUE_DAYS = 45;

    private final BillRepository billRepository;
    private final PaymentRepository paymentRepository;
    private final BillReminderRepository billReminderRepository;

    // ────────────────────────────────────────────────────────────────────────
    // ENDPOINT 1 — Outstanding Bills
    // ────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<OutstandingBillDTO> getOutstandingBills(
            String business, String area, String tier,
            Integer minDays, String customer, Boolean overdueOnly) {

        List<Bill> bills = billRepository.findOutstandingBillsWithCustomer(OUTSTANDING_EXCLUDED);
        Map<Long, List<Payment>> paymentMap = buildPaymentMap(bills);
        LocalDate today = LocalDate.now();

        return bills.stream()
                .map(bill -> toOutstandingBillDTO(bill, paymentMap, today))
                .filter(dto -> matchesFilters(dto, business, area, tier, minDays, customer, overdueOnly))
                .sorted(outstandingComparator())
                .toList();
    }

    // ────────────────────────────────────────────────────────────────────────
    // ENDPOINT 2 — Aging Report
    // ────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, AgingBucketDTO> getAgingReport(String business) {

        List<Bill> bills = billRepository.findOutstandingBillsWithCustomer(OUTSTANDING_EXCLUDED);
        LocalDate today = LocalDate.now();

        // Filter by business if provided
        if (business != null && !business.isBlank()) {
            bills = bills.stream()
                    .filter(b -> b.getBusiness().name().equalsIgnoreCase(business))
                    .toList();
        }

        // Group by business
        Map<String, List<Bill>> byBusiness = bills.stream()
                .collect(Collectors.groupingBy(b -> b.getBusiness().name()));

        Map<String, AgingBucketDTO> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<Bill>> entry : byBusiness.entrySet()) {
            result.put(entry.getKey(), buildAgingBucket(entry.getValue(), today));
        }
        return result;
    }

    // ────────────────────────────────────────────────────────────────────────
    // ENDPOINT 3 — Customer Profile
    // ────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public CustomerProfileDTO getCustomerProfile(String name) {
        if (name == null || name.isBlank()) throw new RuntimeException("Customer name is required");

        List<Bill> allBills = billRepository.findByCustomerNameContainingIgnoreCase(name.trim());
        if (allBills.isEmpty()) throw new RuntimeException("No bills found for customer: " + name);

        // Derive customer info from first bill that has a linked customer entity
        Customer customer = allBills.stream()
                .map(Bill::getCustomer)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);

        // Use customerName from bill if no entity linked
        String displayName = customer != null ? customer.getName() : allBills.get(0).getCustomerName();

        // Payments and reminders for all matched bills
        List<Long> billIds = allBills.stream().map(Bill::getId).toList();
        Map<Long, List<Payment>> paymentMap = buildPaymentMapForIds(billIds);
        List<BillReminder> reminders = billReminderRepository.findByBillIdIn(billIds);

        LocalDate today = LocalDate.now();

        // Unpaid bills only
        List<Bill> unpaidBills = allBills.stream()
                .filter(b -> !Boolean.TRUE.equals(b.getFullyPaid())
                        && !OUTSTANDING_EXCLUDED.contains(b.getStatus()))
                .toList();

        List<OutstandingBillDTO> unpaidDTOs = unpaidBills.stream()
                .map(b -> toOutstandingBillDTO(b, paymentMap, today))
                .sorted(outstandingComparator())
                .toList();

        // Summary
        BigDecimal totalOutstanding = unpaidBills.stream()
                .map(Bill::getBalanceRemaining)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long oldestDays = unpaidBills.stream()
                .mapToLong(b -> ChronoUnit.DAYS.between(b.getBillDate(), today))
                .max().orElse(0);

        LocalDate oldestDate = unpaidBills.stream()
                .map(Bill::getBillDate)
                .min(Comparator.naturalOrder())
                .orElse(null);

        // Payment history — all bills, sorted by date desc
        List<CustomerProfileDTO.PaymentHistoryItem> paymentHistory = allBills.stream()
                .flatMap(b -> paymentMap.getOrDefault(b.getId(), List.of()).stream()
                        .map(p -> CustomerProfileDTO.PaymentHistoryItem.builder()
                                .paymentId(p.getId())
                                .billNumber(b.getBillNumber())
                                .amount(p.getAmount())
                                .paymentType(p.getPaymentType().name())
                                .status(p.getStatus().name())
                                .paymentDate(p.getPaymentDate())
                                .chequeNumber(p.getChequeNumber())
                                .bankName(p.getBankName())
                                .returnReason(p.getReturnReason())
                                .build()))
                .sorted(Comparator.comparing(CustomerProfileDTO.PaymentHistoryItem::getPaymentDate).reversed())
                .toList();

        // Reminders
        List<CustomerProfileDTO.ReminderItem> reminderItems = reminders.stream()
                .map(r -> CustomerProfileDTO.ReminderItem.builder()
                        .reminderDate(r.getReminderDate())
                        .note(r.getNote())
                        .createdBy(r.getCreatedBy().getUsername())
                        .billNumber(r.getBill().getBillNumber())
                        .build())
                .sorted(Comparator.comparing(CustomerProfileDTO.ReminderItem::getReminderDate).reversed())
                .toList();

        return CustomerProfileDTO.builder()
                .customer(CustomerProfileDTO.CustomerInfo.builder()
                        .name(displayName)
                        .phone(customer != null ? customer.getPhone() : null)
                        .area(customer != null ? customer.getArea() : allBills.get(0).getArea())
                        .tier(customer != null ? customer.getTier() : null)
                        .shopType(customer != null ? customer.getShopType() : null)
                        .active(customer != null ? customer.getActive() : null)
                        .build())
                .summary(CustomerProfileDTO.CustomerSummary.builder()
                        .unpaidBillCount(unpaidBills.size())
                        .totalOutstanding(totalOutstanding)
                        .oldestUnpaidDays(oldestDays)
                        .oldestBillDate(oldestDate)
                        .build())
                .unpaidBills(unpaidDTOs)
                .paymentHistory(paymentHistory)
                .reminders(reminderItems)
                .build();
    }

    // ────────────────────────────────────────────────────────────────────────
    // ENDPOINT 4 — Call List Today
    // ────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<CallListItemDTO> getCallList() {
        LocalDate today = LocalDate.now();

        List<Bill> bills = billRepository.findOutstandingBillsWithCustomer(OUTSTANDING_EXCLUDED);
        Map<Long, List<Payment>> paymentMap = buildPaymentMap(bills);

        // Part 1: overdue bills
        List<OutstandingBillDTO> overdueDTOs = bills.stream()
                .map(b -> toOutstandingBillDTO(b, paymentMap, today))
                .filter(OutstandingBillDTO::isOverdue)
                .sorted(callListOverdueComparator())
                .toList();

        Set<Long> overdueIds = overdueDTOs.stream()
                .map(OutstandingBillDTO::getBillId)
                .collect(Collectors.toSet());

        List<CallListItemDTO> callList = new ArrayList<>(
                overdueDTOs.stream().map(dto -> CallListItemDTO.builder()
                        .callReason(dto.getOverdueReason())
                        .customerName(dto.getCustomerName())
                        .phone(dto.getPhone())
                        .area(dto.getArea())
                        .tier(dto.getTier())
                        .business(dto.getBusiness())
                        .billNumber(dto.getBillNumber())
                        .billType(dto.getBillType())
                        .billDate(dto.getBillDate())
                        .balanceRemaining(dto.getBalanceRemaining())
                        .daysSinceBill(dto.getDaysSinceBill())
                        .daysOverdue(dto.getDaysOverdue())
                        .reminderDate(null)
                        .reminderNote(null)
                        .build())
                        .toList()
        );

        // Part 2: reminder calls (not already in Part 1)
        List<BillReminder> dueReminders = billReminderRepository.findDueRemindersForOutstanding(today);
        Map<Long, Bill> billById = bills.stream().collect(Collectors.toMap(Bill::getId, b -> b));

        dueReminders.stream()
                .filter(r -> !overdueIds.contains(r.getBill().getId()))
                .filter(r -> billById.containsKey(r.getBill().getId()))
                .sorted(reminderComparator(today))
                .forEach(r -> {
                    Bill b = billById.get(r.getBill().getId());
                    Customer c = b.getCustomer();
                    long daysSince = ChronoUnit.DAYS.between(b.getBillDate(), today);
                    callList.add(CallListItemDTO.builder()
                            .callReason("REMINDER DUE")
                            .customerName(b.getCustomerName())
                            .phone(c != null ? c.getPhone() : null)
                            .area(b.getArea() != null ? b.getArea() : (c != null ? c.getArea() : null))
                            .tier(c != null ? c.getTier() : null)
                            .business(b.getBusiness().name())
                            .billNumber(b.getBillNumber())
                            .billType(b.getBillType().name())
                            .billDate(b.getBillDate())
                            .balanceRemaining(b.getBalanceRemaining())
                            .daysSinceBill(daysSince)
                            .daysOverdue(0)
                            .reminderDate(r.getReminderDate())
                            .reminderNote(r.getNote())
                            .build());
                });

        return callList;
    }

    // ────────────────────────────────────────────────────────────────────────
    // Helpers
    // ────────────────────────────────────────────────────────────────────────

    private record OverdueInfo(boolean isOverdue, String reason, long daysOverdue) {}

    private OverdueInfo computeOverdue(Bill bill, List<Payment> payments, LocalDate today) {
        long daysSince = ChronoUnit.DAYS.between(bill.getBillDate(), today);

        if (bill.getBillType() == BillType.CASH) {
            if (!Boolean.TRUE.equals(bill.getFullyPaid())) {
                return new OverdueInfo(true, "CASH UNPAID", daysSince);
            }
            return new OverdueInfo(false, "MONITORING", 0);
        }

        // CREDIT bill
        boolean hasBounced = payments.stream()
                .anyMatch(p -> p.getPaymentType() == PaymentType.CHEQUE
                        && p.getStatus() == PaymentStatus.RETURNED);

        boolean hasNonReturnedPayment = payments.stream()
                .anyMatch(p -> p.getStatus() != PaymentStatus.RETURNED);

        if (hasBounced) {
            long daysOver = Math.max(0, daysSince - CREDIT_OVERDUE_DAYS);
            return new OverdueInfo(daysSince > CREDIT_OVERDUE_DAYS && !hasNonReturnedPayment,
                    "CHEQUE BOUNCED", daysOver);
        }

        if (daysSince > CREDIT_OVERDUE_DAYS && !hasNonReturnedPayment) {
            return new OverdueInfo(true, "NO CHEQUE RECEIVED", daysSince - CREDIT_OVERDUE_DAYS);
        }

        return new OverdueInfo(false, "MONITORING", 0);
    }

    private OutstandingBillDTO toOutstandingBillDTO(Bill bill, Map<Long, List<Payment>> paymentMap, LocalDate today) {
        List<Payment> payments = paymentMap.getOrDefault(bill.getId(), List.of());
        Customer c = bill.getCustomer();
        OverdueInfo info = computeOverdue(bill, payments, today);
        long daysSince = ChronoUnit.DAYS.between(bill.getBillDate(), today);

        return OutstandingBillDTO.builder()
                .billId(bill.getId())
                .billNumber(bill.getBillNumber())
                .customerName(bill.getCustomerName())
                .phone(c != null ? c.getPhone() : null)
                .area(bill.getArea() != null ? bill.getArea() : (c != null ? c.getArea() : null))
                .tier(c != null ? c.getTier() : null)
                .shopType(c != null ? c.getShopType() : null)
                .business(bill.getBusiness().name())
                .billType(bill.getBillType().name())
                .billDate(bill.getBillDate())
                .totalAmount(bill.getTotalAmount())
                .amountPaid(bill.getAmountPaid())
                .balanceRemaining(bill.getBalanceRemaining())
                .status(bill.getStatus().name())
                .daysSinceBill(daysSince)
                .isOverdue(info.isOverdue())
                .overdueReason(info.reason())
                .daysOverdue(info.daysOverdue())
                .build();
    }

    private AgingBucketDTO buildAgingBucket(List<Bill> bills, LocalDate today) {
        int c0 = 0, c31 = 0, c61 = 0, c90 = 0;
        BigDecimal a0 = BigDecimal.ZERO, a31 = BigDecimal.ZERO, a61 = BigDecimal.ZERO, a90 = BigDecimal.ZERO;
        long oldest = 0;

        for (Bill b : bills) {
            long days = ChronoUnit.DAYS.between(b.getBillDate(), today);
            BigDecimal bal = b.getBalanceRemaining();
            if (days > oldest) oldest = days;
            if (days <= 30)      { c0++;  a0  = a0.add(bal); }
            else if (days <= 60) { c31++; a31 = a31.add(bal); }
            else if (days <= 90) { c61++; a61 = a61.add(bal); }
            else                 { c90++; a90 = a90.add(bal); }
        }

        BigDecimal total = a0.add(a31).add(a61).add(a90);
        return AgingBucketDTO.builder()
                .count0to30(c0).amount0to30(a0)
                .count31to60(c31).amount31to60(a31)
                .count61to90(c61).amount61to90(a61)
                .count90plus(c90).amount90plus(a90)
                .totalBills(bills.size())
                .totalOutstanding(total)
                .oldestDays(oldest)
                .build();
    }

    private boolean matchesFilters(OutstandingBillDTO dto, String business, String area,
                                    String tier, Integer minDays, String customer, Boolean overdueOnly) {
        if (business != null && !business.isBlank()
                && !dto.getBusiness().equalsIgnoreCase(business)) return false;
        if (area != null && !area.isBlank()
                && (dto.getArea() == null || !dto.getArea().toLowerCase().contains(area.toLowerCase()))) return false;
        if (tier != null && !tier.isBlank()
                && (dto.getTier() == null || !dto.getTier().toLowerCase().contains(tier.toLowerCase()))) return false;
        if (minDays != null && dto.getDaysSinceBill() < minDays) return false;
        if (customer != null && !customer.isBlank()
                && (dto.getCustomerName() == null || !dto.getCustomerName().toLowerCase().contains(customer.toLowerCase()))) return false;
        if (Boolean.TRUE.equals(overdueOnly) && !dto.isOverdue()) return false;
        return true;
    }

    private Map<Long, List<Payment>> buildPaymentMap(List<Bill> bills) {
        if (bills.isEmpty()) return Map.of();
        List<Long> ids = bills.stream().map(Bill::getId).toList();
        return buildPaymentMapForIds(ids);
    }

    private Map<Long, List<Payment>> buildPaymentMapForIds(List<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return paymentRepository.findByBillIdIn(ids).stream()
                .collect(Collectors.groupingBy(p -> p.getBill().getId()));
    }

    // ── Sort helpers ─────────────────────────────────────────────────────────

    private Comparator<OutstandingBillDTO> outstandingComparator() {
        return Comparator
                .comparing(OutstandingBillDTO::isOverdue, Comparator.reverseOrder())
                .thenComparingInt(dto -> overdueReasonPriority(dto.getOverdueReason()))
                .thenComparingInt(dto -> tierPriority(dto.getTier()))
                .thenComparingLong(dto -> -dto.getDaysOverdue());
    }

    private Comparator<OutstandingBillDTO> callListOverdueComparator() {
        return Comparator
                .comparingInt((OutstandingBillDTO dto) -> overdueReasonPriority(dto.getOverdueReason()))
                .thenComparingInt(dto -> tierPriority(dto.getTier()))
                .thenComparingLong(dto -> -dto.getDaysOverdue());
    }

    private Comparator<BillReminder> reminderComparator(LocalDate today) {
        return Comparator
                .comparingInt((BillReminder r) -> tierPriority(
                        r.getBill().getCustomer() != null ? r.getBill().getCustomer().getTier() : null))
                .thenComparingLong(r -> ChronoUnit.DAYS.between(r.getReminderDate(), today));
    }

    private int overdueReasonPriority(String reason) {
        return switch (reason == null ? "" : reason) {
            case "CHEQUE BOUNCED"     -> 1;
            case "CASH UNPAID"        -> 2;
            case "NO CHEQUE RECEIVED" -> 3;
            default                   -> 4; // MONITORING
        };
    }

    private int tierPriority(String tier) {
        if (tier == null) return 6;
        return switch (tier.toLowerCase()) {
            case "platinum"         -> 1;
            case "gold"             -> 2;
            case "silver"           -> 3;
            case "bronze"           -> 4;
            case "emergency top-up" -> 5;
            default                 -> 6;
        };
    }
}
