package com.multi.finance.invoicing.service;

import com.multi.finance.entity.Bill;
import com.multi.finance.entity.User;
import com.multi.finance.enums.BillSource;
import com.multi.finance.enums.BillStatus;
import com.multi.finance.enums.BillType;
import com.multi.finance.enums.BusinessType;
import com.multi.finance.enums.UserRole;
import com.multi.finance.invoicing.entity.Invoice;
import com.multi.finance.invoicing.enums.InvoiceMethod;
import com.multi.finance.repository.BillRepository;
import com.multi.finance.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Raises a bill in the bills section for an invoice created in the invoicing module,
 * so the money can be collected through the existing payment workflow.
 *
 * The bill is deliberately marked {@link BillSource#INVOICE} rather than SYSTEM. The
 * stock workflows — Summary Load, Stock Item Entry, End-of-Month Linking — all key off
 * SYSTEM bills, and the invoicing module has already moved the stock for these. Reusing
 * SYSTEM would double-count it.
 */
@Service
@RequiredArgsConstructor
public class InvoiceBillBridge {

    private final BillRepository billRepository;
    private final UserRepository userRepository;
    private final InvoiceNumberService numbering;
    private final com.multi.finance.repository.DeliveryRunRepository deliveryRunRepository;

    /**
     * @param netTotal what is actually owed — the invoice's net, after all discounts
     * @return the saved bill
     */
    public Attachment raiseBill(Invoice invoice, BigDecimal netTotal, String createdByUsername) {
        return raiseBill(invoice, netTotal, createdByUsername, null, null);
    }

    /**
     * @param mode  how the goods go out, for a bill this raises
     * @param runId the round they go out on, which decides the mode on its own
     */
    public Attachment raiseBill(Invoice invoice, BigDecimal netTotal, String createdByUsername,
                                com.multi.finance.enums.DeliveryMode mode, Long runId) {
        // The invoice already carries the number both records share.
        String billNumber = invoice.getInvoiceNo();
        BusinessType business = numbering.businessFor(invoice.getMethod());

        // Any bill holding this number blocks it, cancelled or not: a number stays with
        // the bill it was issued to until that bill is deleted.
        Bill existing = billRepository
                .findActiveByBillNumberAndBusiness(billNumber, business).orElse(null);
        if (existing != null) {
            // Raised by invoicing already — that is a real duplicate, not a hand-typed bill
            // waiting for its stock.
            if (existing.getBillSource() == BillSource.INVOICE) {
                throw new IllegalStateException(
                        "Invoice " + billNumber + " has already been entered under " + business + ".");
            }
            // Entered by hand without its stock moving. Attach to it: the money is already
            // being collected there, and a second bill would double it.
            //
            // Its delivery is left exactly as it was. That was recorded when the bill was
            // entered, by whoever was standing there; the invoice is raised later and
            // knows less about how the goods actually went.
            return new Attachment(existing, true,
                    netTotal.subtract(existing.getTotalAmount() == null
                            ? BigDecimal.ZERO : existing.getTotalAmount()));
        }

        // A cancelled round cannot take new bills, and silently dropping the link would
        // leave the bill claiming a delivery it never had.
        com.multi.finance.entity.DeliveryRun run = null;
        if (runId != null) {
            run = deliveryRunRepository.findById(runId)
                    .orElseThrow(() -> new IllegalStateException("Delivery run not found"));
            if (run.getStatus() == com.multi.finance.enums.DeliveryRunStatus.CANCELLED) {
                throw new IllegalStateException(
                        "That delivery run was cancelled — bills cannot join it.");
            }
        }

        User enteredBy = userRepository.findByUsername(createdByUsername)
                .orElseThrow(() -> new IllegalStateException(
                        "Could not identify the user raising this invoice: " + createdByUsername));

        Bill bill = Bill.builder()
                .billNumber(billNumber)
                .business(business)
                .division(divisionFor(enteredBy))
                .billSource(BillSource.INVOICE)
                .billType(invoice.getInvoiceType() == com.multi.finance.invoicing.enums.InvoiceType.CASH
                        ? BillType.CASH : BillType.CREDIT)
                .customerName(invoice.getCustomer().getName())
                .customer(invoice.getCustomer())
                .totalAmount(netTotal)
                .area(normaliseArea(invoice.getCustomer().getArea()))
                .status(BillStatus.CREATED)
                .deliveryMode(run != null ? com.multi.finance.enums.DeliveryMode.ROUTE
                        : (mode != null ? mode : com.multi.finance.enums.DeliveryMode.UNSPECIFIED))
                .deliveryRun(run)
                .enteredBy(enteredBy)
                .billDate(invoice.getInvoiceDate())
                .notes("Raised from invoice " + invoice.getInvoiceNo())
                .amountPaid(BigDecimal.ZERO)
                .balanceRemaining(netTotal)
                .fullyPaid(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        return new Attachment(billRepository.save(bill), false, BigDecimal.ZERO);
    }

    /**
     * @param bill      the bill the invoice is attached to
     * @param linked    true when it already existed and the invoice only moves the stock
     * @param variance  invoice net minus the existing bill's total; zero for a new bill
     */
    public record Attachment(Bill bill, boolean linked, BigDecimal variance) {}

    /**
     * Rewrites the bill's total after the invoice was edited. The balance is recomputed
     * from what has already been paid, so an edit can't quietly leave the bill collecting
     * the old amount.
     *
     * If the new total lands below what has already been collected the balance goes
     * negative — the customer has overpaid. That is left visible rather than clamped to
     * zero, because hiding it would lose real money owed back to them.
     */
    public void syncBillTotal(Invoice invoice, BigDecimal newTotal) {
        if (invoice.getBillId() == null) return;
        // The bill was entered by hand and is collected on its own terms — invoicing only
        // moved its stock, so it has no business rewriting the amount.
        if (invoice.isBillLinkedExisting()) return;
        Bill bill = billRepository.findById(invoice.getBillId()).orElse(null);
        if (bill == null) return;

        bill.setTotalAmount(newTotal);
        com.multi.finance.service.BillBalance.recompute(bill);
        billRepository.save(bill);
    }

    /** Mirrors how the bills section assigns division on manual entry. */
    private String divisionFor(User user) {
        if (user.getRole() == UserRole.SHOP_ACCOUNTANT) return "SHOP";
        return "STORE";
    }

    private String normaliseArea(String area) {
        return (area == null || area.isBlank()) ? null : area.trim().toUpperCase();
    }
}
