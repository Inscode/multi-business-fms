package com.multi.finance.service.impl;

import com.multi.finance.dto.response.CustomerHealthResponse;
import com.multi.finance.entity.Bill;
import com.multi.finance.entity.BillReturn;
import com.multi.finance.entity.Customer;
import com.multi.finance.entity.Payment;
import com.multi.finance.enums.BillStatus;
import com.multi.finance.enums.BusinessType;
import com.multi.finance.enums.PaymentStatus;
import com.multi.finance.enums.ReturnType;
import com.multi.finance.repository.BillReturnRepository;
import com.multi.finance.repository.BillRepository;
import com.multi.finance.repository.CustomerRepository;
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
 * Whether a customer is safe to keep selling to on credit, judged on what they have
 * actually done rather than on what is outstanding today.
 *
 * <p>Read-only and computed on request. Nothing is stored: a stored rating is a rating
 * that can quietly disagree with the ledger, and this one is meant to be trusted at the
 * moment somebody is deciding whether to load a lorry.
 *
 * <p>Split per business throughout. A shop can be reliable on stationery and slow on
 * Rainco, and a blended figure hides the one split worth knowing.
 */
@Service
@RequiredArgsConstructor
public class CustomerHealthService {

    private final BillRepository billRepository;
    private final PaymentRepository paymentRepository;
    private final BillReturnRepository billReturnRepository;
    private final CustomerRepository customerRepository;

    /**
     * Past this a balance is late rather than merely open. Matches the aging report and
     * the terms stamped on the bill — deliberately short, so being paid at 60 still
     * leaves the company whole.
     */
    private static final int OVERDUE_DAYS = CreditTerms.SEAL_DAYS;

    /**
     * Where a customer stops being slow and starts being a risk.
     *
     * <p>60 rather than the 45 on the bill, because 45 is a negotiating position and
     * nobody treats it as a deadline. And below 70, because 70 is when the company has
     * already paid the principal — a rating that only fired at 70 would be telling you
     * about a cost you had already borne.
     */
    private static final int RISK_DAYS = CreditTerms.DANGER_DAYS;

    /** The company's own deadline to pay the principal. Past this the sale cost money. */
    private static final int FUNDING_DAYS = CreditTerms.SUPPLIER_DAYS;

    /** The window that counts as "lately" when comparing against the whole history. */
    private static final int RECENT_MONTHS = 6;

    /**
     * Below this many settled bills, an average is an anecdote. Ratings fall back to
     * what is open right now rather than reading a trend into two data points.
     */
    private static final int MIN_BILLS_FOR_TREND = 3;

    /** Statuses that mean nothing is owed, or that the bill is not real. */
    private static final List<BillStatus> CLOSED =
            List.of(BillStatus.COMPLETED, BillStatus.AWAITING_CONFIRMATION, BillStatus.CANCELLED);

    // ── Ratings ─────────────────────────────────────────────────────────────
    public static final String GOOD = "GOOD";
    public static final String WATCH = "WATCH";
    public static final String CAREFUL = "CAREFUL";

    /**
     * One customer's record, business by business.
     *
     * @param id the customer to look at
     */
    @Transactional(readOnly = true)
    public CustomerHealthResponse forCustomer(Long id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Customer not found"));

        // Matched on id or on name: bills raised before customers were records of their
        // own, and bills brought in by import, carry only the typed name. Leaving those
        // out would rate a long-standing customer on their last few months.
        List<Bill> bills = billRepository.findAllForCustomer(id, customer.getName());
        return build(customer.getId(), customer.getName(), customer.getArea(),
                     customer.getPhone(), customer.getTier(), bills);
    }

    /**
     * Every customer's rating for one business, worst first.
     *
     * <p>The list a manager scans before a round goes out, rather than a lookup done one
     * name at a time once the goods are already on the lorry.
     */
    @Transactional(readOnly = true)
    public List<CustomerHealthResponse> forBusiness(BusinessType business) {
        Map<String, List<Bill>> byCustomer = new LinkedHashMap<>();
        for (Bill b : billRepository.findByBusinessOrderByBillDateDesc(business)) {
            if (b.getStatus() == BillStatus.CANCELLED) continue;
            byCustomer.computeIfAbsent(key(b), k -> new ArrayList<>()).add(b);
        }

        List<CustomerHealthResponse> out = new ArrayList<>();
        for (List<Bill> group : byCustomer.values()) {
            Bill first = group.get(0);
            out.add(build(first.getCustomer() != null ? first.getCustomer().getId() : null,
                          first.getCustomerName(),
                          first.getArea(),
                          first.getCustomer() != null ? first.getCustomer().getPhone() : null,
                          first.getCustomer() != null ? first.getCustomer().getTier() : null,
                          group));
        }

        // Worst first, and within a rating the one owing most. The point of the list is
        // what to deal with, so what needs dealing with is at the top.
        out.sort(Comparator
                .comparingInt((CustomerHealthResponse c) -> ratingRank(c.getOverallRating()))
                .thenComparing(c -> outstandingOf(c).negate()));
        return out;
    }

    /** Groups by customer id where there is one, and by name where there is not. */
    private static String key(Bill b) {
        return b.getCustomer() != null
                ? "#" + b.getCustomer().getId()
                : "n:" + (b.getCustomerName() == null ? "" : b.getCustomerName().trim().toUpperCase());
    }

    private static int ratingRank(String rating) {
        if (CAREFUL.equals(rating)) return 0;
        if (WATCH.equals(rating)) return 1;
        return 2;
    }

    private static BigDecimal outstandingOf(CustomerHealthResponse c) {
        return c.getBusinesses().stream()
                .map(CustomerHealthResponse.BusinessHealth::getCurrentOutstanding)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ── Assembly ────────────────────────────────────────────────────────────

    private CustomerHealthResponse build(Long customerId, String name, String area,
                                         String phone, String tier, List<Bill> allBills) {
        List<Bill> real = allBills.stream()
                .filter(b -> b.getStatus() != BillStatus.CANCELLED)
                // A bill collected on a hand-written one is not this customer failing to
                // pay; the money comes in on the other bill. Counting it here would
                // show a permanent unpaid balance that nobody is owed.
                .filter(b -> b.getSettledOn() == null)
                .toList();

        List<Long> billIds = real.stream().map(Bill::getId).toList();
        Map<Long, List<Payment>> paymentsByBill = new LinkedHashMap<>();
        Map<Long, List<BillReturn>> returnsByBill = new LinkedHashMap<>();
        if (!billIds.isEmpty()) {
            for (Payment p : paymentRepository.findByBillIdIn(billIds)) {
                paymentsByBill.computeIfAbsent(p.getBill().getId(), k -> new ArrayList<>()).add(p);
            }
            for (BillReturn r : billReturnRepository.findByBillIdIn(billIds)) {
                returnsByBill.computeIfAbsent(r.getBill().getId(), k -> new ArrayList<>()).add(r);
            }
        }

        Map<BusinessType, List<Bill>> byBusiness = new LinkedHashMap<>();
        for (Bill b : real) {
            byBusiness.computeIfAbsent(b.getBusiness(), k -> new ArrayList<>()).add(b);
        }

        List<CustomerHealthResponse.BusinessHealth> businesses = byBusiness.entrySet().stream()
                .map(e -> businessHealth(e.getKey(), e.getValue(), paymentsByBill, returnsByBill))
                .sorted(Comparator.comparing(CustomerHealthResponse.BusinessHealth::getBusiness))
                .toList();

        String overall = businesses.stream()
                .map(CustomerHealthResponse.BusinessHealth::getRating)
                .min(Comparator.comparingInt(CustomerHealthService::ratingRank))
                .orElse(GOOD);

        return CustomerHealthResponse.builder()
                .customerId(customerId)
                .customerName(name)
                .area(area)
                .phone(phone)
                .tier(tier)
                .businesses(businesses)
                .overallRating(overall)
                .build();
    }

    private CustomerHealthResponse.BusinessHealth businessHealth(
            BusinessType business, List<Bill> bills,
            Map<Long, List<Payment>> paymentsByBill,
            Map<Long, List<BillReturn>> returnsByBill) {

        LocalDate today = LocalDate.now();
        LocalDate recentFrom = today.minusMonths(RECENT_MONTHS);

        List<Integer> allDays = new ArrayList<>();
        List<Integer> recentDays = new ArrayList<>();

        BigDecimal totalBilled = BigDecimal.ZERO;
        BigDecimal totalPaid = BigDecimal.ZERO;
        BigDecimal outstanding = BigDecimal.ZERO;
        BigDecimal overdue = BigDecimal.ZERO;
        BigDecimal damage = BigDecimal.ZERO;

        int openBills = 0;
        int bounced = 0;
        int partials = 0;
        Integer oldestOpenDays = null;
        // Counted per bill against its own terms, since cash and credit are not due at
        // the same time and one average across both hides whichever is worse.
        int overTerms = 0;
        int badlyLate = 0;
        int worstOpenOverTerms = 0;
        LocalDate lastBounce = null;
        LocalDate firstBill = null;
        LocalDate lastBill = null;

        for (Bill bill : bills) {
            totalBilled = totalBilled.add(nz(bill.getTotalAmount()));
            totalPaid = totalPaid.add(nz(bill.getAmountPaid()));

            LocalDate billDate = bill.getBillDate();
            if (billDate != null) {
                if (firstBill == null || billDate.isBefore(firstBill)) firstBill = billDate;
                if (lastBill == null || billDate.isAfter(lastBill)) lastBill = billDate;
            }

            List<Payment> payments = paymentsByBill.getOrDefault(bill.getId(), List.of());
            for (Payment p : payments) {
                // A returned cheque is the strongest single signal there is: the customer
                // said the money was there and it was not.
                if (p.getStatus() == PaymentStatus.RETURNED) {
                    bounced++;
                    LocalDate on = p.getPaymentDate();
                    if (on != null && (lastBounce == null || on.isAfter(lastBounce))) lastBounce = on;
                }
                if (Boolean.TRUE.equals(p.getIsPartial())
                        && p.getStatus() != PaymentStatus.REJECTED
                        && p.getStatus() != PaymentStatus.RETURNED) {
                    partials++;
                }
            }

            for (BillReturn r : returnsByBill.getOrDefault(bill.getId(), List.of())) {
                if (r.getReturnType() == ReturnType.DAMAGE
                        && r.getStatus() != null && r.getStatus().reducesBill()) {
                    damage = damage.add(nz(r.getApprovedAmount()));
                }
            }

            boolean settled = Boolean.TRUE.equals(bill.getFullyPaid())
                    || CLOSED.contains(bill.getStatus());

            if (settled) {
                // Dated from the payment that finished it, not the first one: a customer
                // who pays a tenth on day two and the rest on day ninety took ninety.
                LocalDate settledOn = payments.stream()
                        .filter(p -> p.getStatus() == PaymentStatus.CONFIRMED)
                        .map(Payment::getPaymentDate)
                        .filter(java.util.Objects::nonNull)
                        .max(Comparator.naturalOrder())
                        .orElse(null);
                if (billDate != null && settledOn != null && !settledOn.isBefore(billDate)) {
                    int days = (int) ChronoUnit.DAYS.between(billDate, settledOn);
                    allDays.add(days);
                    if (settledOn.isAfter(recentFrom)) recentDays.add(days);

                    // Judged against what this bill was actually due on. Cash is due when
                    // the goods change hands; measuring it on the credit run reports a
                    // cash sale collected three weeks late as comfortably on time.
                    boolean cash = bill.getBillType() == com.multi.finance.enums.BillType.CASH;
                    if (days > CreditTerms.dangerDays(cash)) overTerms++;
                    if (days > CreditTerms.supplierDays(cash)) badlyLate++;
                }
            } else {
                BigDecimal bal = nz(bill.getBalanceRemaining());
                if (bal.compareTo(BigDecimal.ZERO) > 0) {
                    openBills++;
                    outstanding = outstanding.add(bal);
                    int age = billDate == null ? 0 : (int) ChronoUnit.DAYS.between(billDate, today);
                    boolean cash = bill.getBillType() == com.multi.finance.enums.BillType.CASH;
                    // Overdue on its own terms: a cash bill open eight days is late, a
                    // credit one is not.
                    if (age >= CreditTerms.sealDays(cash)) overdue = overdue.add(bal);
                    // How far past its own line, so a cash bill at 20 days outranks a
                    // credit bill at 50 — which is the right way round.
                    int over = age - CreditTerms.sealDays(cash);
                    if (over > worstOpenOverTerms) worstOpenOverTerms = over;
                    if (oldestOpenDays == null || age > oldestOpenDays) oldestOpenDays = age;
                }
            }
        }

        int settledCount = allDays.size();
        Integer overTermsPct = settledCount > 0
                ? (int) Math.round(overTerms * 100.0 / settledCount) : null;

        Integer avgAll = average(allDays);
        Integer avgRecent = average(recentDays);
        Integer worst = allDays.stream().max(Comparator.naturalOrder()).orElse(null);

        BigDecimal damagePct = totalBilled.compareTo(BigDecimal.ZERO) > 0
                ? damage.multiply(new BigDecimal("100"))
                        .divide(totalBilled, 1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        CustomerHealthResponse.BusinessHealth health = CustomerHealthResponse.BusinessHealth.builder()
                .business(business.name())
                .avgDaysToSettle(avgAll)
                .avgDaysToSettleRecent(avgRecent)
                .worstDaysToSettle(worst)
                .overTermsCount(overTerms)
                .badlyLateCount(badlyLate)
                .overTermsPct(overTermsPct)
                .worstOpenOverTerms(worstOpenOverTerms)
                .settledBillCount(allDays.size())
                .currentOutstanding(outstanding)
                .openBillCount(openBills)
                .oldestOpenDays(oldestOpenDays)
                .overdueAmount(overdue)
                .bouncedChequeCount(bounced)
                .lastBouncedChequeDate(lastBounce)
                .partialPaymentCount(partials)
                .damageReturnPct(damagePct)
                .damageReturnAmount(damage)
                .totalBilled(totalBilled)
                .totalPaid(totalPaid)
                .billCount(bills.size())
                .firstBillDate(firstBill)
                .lastBillDate(lastBill)
                .daysSinceLastBill(lastBill == null ? null
                        : (int) ChronoUnit.DAYS.between(lastBill, today))
                .build();

        rate(health);
        return health;
    }

    /**
     * Turns the figures into a rating and says why.
     *
     * <p>Rate and recency, never raw counts. A customer who bought sixty times and was
     * late twice is not the same as one who bought twice and bounced a cheque, and a
     * count cannot tell them apart. Likewise a bad stretch two years ago matters less
     * than one happening now, so the recent window carries more weight than the history.
     *
     * <p>The reasons are the point as much as the rating is. Whoever reads this is about
     * to overrule it or act on it, and either way they need to know what it saw.
     */
    private void rate(CustomerHealthResponse.BusinessHealth h) {
        List<String> reasons = new ArrayList<>();
        int careful = 0;
        int watch = 0;

        // ── What is wrong right now ─────────────────────────────────────────
        // Measured as days past the bill's own terms, so a cash bill open twenty days
        // outranks a credit bill open fifty — which is the right way round, and the
        // wrong way round before.
        Integer over = h.getWorstOpenOverTerms();
        if (over != null && over > 0) {
            String age = h.getOldestOpenDays() + " days";
            if (over > FUNDING_DAYS - OVERDUE_DAYS) {
                careful++;
                reasons.add("A bill has been open " + age
                          + " — well past what it was due on.");
            } else if (over > RISK_DAYS - OVERDUE_DAYS) {
                careful++;
                reasons.add("A bill has been open " + age + ", past its terms.");
            } else {
                watch++;
                reasons.add("Oldest open bill is " + age + ", past its terms.");
            }
        }

        if (h.getOverdueAmount().compareTo(BigDecimal.ZERO) > 0
                && h.getCurrentOutstanding().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal share = h.getOverdueAmount()
                    .multiply(new BigDecimal("100"))
                    .divide(h.getCurrentOutstanding(), 0, RoundingMode.HALF_UP);
            if (share.intValue() >= 60) {
                careful++;
                reasons.add("Rs " + h.getOverdueAmount().toBigInteger() + " of what is open — "
                          + share + "% — is past its terms.");
            }
        }

        // ── Cheques ─────────────────────────────────────────────────────────
        // Recency matters more than the tally. One bounce last month says more about
        // today than three in a year that has since gone quiet.
        if (h.getBouncedChequeCount() > 0) {
            boolean recent = h.getLastBouncedChequeDate() != null
                    && h.getLastBouncedChequeDate().isAfter(LocalDate.now().minusMonths(RECENT_MONTHS));
            String when = h.getLastBouncedChequeDate() == null ? ""
                    : " Last was " + h.getLastBouncedChequeDate() + ".";
            if (recent || h.getBouncedChequeCount() >= 2) {
                careful++;
                reasons.add(h.getBouncedChequeCount() == 1
                        ? "A cheque was returned." + when
                        : h.getBouncedChequeCount() + " cheques have been returned." + when);
            } else {
                watch++;
                reasons.add("A cheque was returned, but not lately." + when);
            }
        }

        // ── How long they take, and whether it is getting worse ─────────────
        if (h.getSettledBillCount() >= MIN_BILLS_FOR_TREND) {
            Integer recent = h.getAvgDaysToSettleRecent();
            Integer all = h.getAvgDaysToSettle();
            int judge = recent != null ? recent : (all == null ? 0 : all);

            // How often they broke their own terms, rather than what the raw average
            // came to. A customer buying cash and credit averages the two together, and
            // the mean lands between the lines without touching either — punctual by
            // arithmetic, late on half their bills in fact.
            int pct = h.getOverTermsPct() == null ? 0 : h.getOverTermsPct();

            if (h.getBadlyLateCount() > 0 && pct >= 40) {
                careful++;
                reasons.add(h.getBadlyLateCount() + " of " + h.getSettledBillCount()
                          + " bills were paid long past their terms.");
            } else if (pct >= 50) {
                careful++;
                reasons.add(pct + "% of their bills ran past terms.");
            } else if (pct >= 25) {
                watch++;
                reasons.add(pct + "% of their bills ran past terms.");
            }

            // Said as well, because it is what anyone asks first — but it no longer
            // decides the rating on its own.
            if (judge > 0) {
                reasons.add("Settles in " + judge + " days on average.");
            }

            if (h.getWorstDaysToSettle() != null && h.getWorstDaysToSettle() > FUNDING_DAYS
                    && pct < 25) {
                reasons.add("Once took " + h.getWorstDaysToSettle() + " days, though "
                          + "usually quicker.");
            }

            // Said separately: a customer still inside terms but slowing down is the one
            // worth catching early, and the average alone would read as fine.
            if (recent != null && all != null && recent - all >= 15) {
                watch++;
                reasons.add("Slowing down — " + recent + " days lately against "
                          + all + " overall.");
            } else if (recent != null && all != null && all - recent >= 15) {
                reasons.add("Improving — " + recent + " days lately against "
                          + all + " overall.");
            }
        } else if (h.getBillCount() <= 2) {
            reasons.add("Only " + h.getBillCount()
                      + (h.getBillCount() == 1 ? " bill" : " bills")
                      + " so far — not enough history to judge.");
        }

        // ── Paying in pieces ────────────────────────────────────────────────
        if (h.getBillCount() > 0) {
            int pct = h.getPartialPaymentCount() * 100 / Math.max(1, h.getBillCount());
            if (pct >= 60 && h.getPartialPaymentCount() >= 3) {
                watch++;
                reasons.add("Usually pays in instalments (" + h.getPartialPaymentCount()
                          + " part payments across " + h.getBillCount() + " bills).");
            }
        }

        // ── Damage ──────────────────────────────────────────────────────────
        // Not a payment problem, but it is money off the same relationship, and a shop
        // sending back a tenth of what it buys is worth knowing about before the next load.
        if (h.getDamageReturnPct().compareTo(new BigDecimal("10")) >= 0) {
            watch++;
            reasons.add("Sends back " + h.getDamageReturnPct() + "% of what it buys as damage.");
        }

        if (careful > 0) {
            h.setRating(CAREFUL);
        } else if (watch > 0) {
            h.setRating(WATCH);
        } else {
            h.setRating(GOOD);
            if (reasons.isEmpty()) {
                reasons.add(h.getSettledBillCount() > 0
                        ? "Pays on time, nothing outstanding past terms."
                        : "Nothing against them.");
            }
        }
        h.setReasons(reasons);
    }

    private static Integer average(List<Integer> values) {
        if (values.isEmpty()) return null;
        int sum = 0;
        for (int v : values) sum += v;
        return Math.round((float) sum / values.size());
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
