package com.multi.finance.invoicing.service;

import java.time.LocalDateTime;
import java.util.Set;
import com.multi.finance.invoicing.dto.request.InvoiceRequest;
import com.multi.finance.invoicing.dto.request.QuoteRequest;
import com.multi.finance.invoicing.dto.response.QuoteResponse;
import com.multi.finance.invoicing.dto.response.InvoiceLineResponse;
import com.multi.finance.invoicing.dto.response.InvoicePrintResponse;
import com.multi.finance.invoicing.dto.response.InvoiceResponse;
import com.multi.finance.invoicing.dto.response.InvoiceSummaryResponse;
import com.multi.finance.invoicing.entity.*;
import com.multi.finance.entity.Customer;
import com.multi.finance.invoicing.enums.CategoryType;
import com.multi.finance.invoicing.enums.InvoiceMethod;
import com.multi.finance.invoicing.enums.InvoiceSource;
import com.multi.finance.invoicing.enums.InvoiceType;
import com.multi.finance.invoicing.enums.StockMovementType;
import com.multi.finance.invoicing.repository.*;
import com.multi.finance.repository.CustomerRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class InvoiceService {

    private final InvoiceRepository invoiceRepo;
    private final FreeIssuePolicy freeIssuePolicy;
    private final CustomerRepository customerRepo;
    private final ItemRepository itemRepo;
    private final BrandRepository brandRepo;
    private final DiscountSlabRepository slabRepo;
    private final StockMovementRepository movementRepo;
    private final SystemSettingsRepository settingsRepo;
    private final InvoiceBillBridge billBridge;
    private final InvoiceNumberService numbering;
    private final DiscountEngineService engine = new DiscountEngineService();

    // Sentinel grouping key: all PLASTIC items are combined into one group (no per-brand/supplier
    // slab discount) since plastic only gets a single overall discount on the combined total.
    private static final Long PLASTIC_GROUP_KEY = -1L;

    private Long groupKey(Brand brand) {
        return brand.getCategory() == CategoryType.PLASTIC ? PLASTIC_GROUP_KEY : brand.getId();
    }

    @Transactional
    public InvoiceResponse create(InvoiceRequest req, String createdBy) {
        return create(req, createdBy, InvoiceSource.MANUAL);
    }

    @Transactional
    public InvoiceResponse create(InvoiceRequest req, String createdBy, InvoiceSource source) {
        return create(req, createdBy, source, null);
    }

    @Transactional
    public InvoiceResponse create(InvoiceRequest req, String createdBy, InvoiceSource source,
                                   Long importBatchId) {
        Customer customer = customerRepo.findById(req.getCustomerId())
                .orElseThrow(() -> new EntityNotFoundException("Customer not found"));

        // Load all relevant brands with slabs up front (avoid N+1)
        List<Brand> allBrands = brandRepo.findAllActiveWithSlabs();
        Map<Long, List<DiscountSlab>> brandSlabs = new HashMap<>();
        for (Brand b : allBrands) {
            brandSlabs.put(b.getId(), b.getSlabs().stream()
                    .sorted(Comparator.comparingInt(s -> s.getSortOrder() != null ? s.getSortOrder() : 0))
                    .toList());
        }

        Invoice invoice = new Invoice();
        invoice.setMethod(req.getMethod());
        invoice.setInvoiceDate(req.getInvoiceDate());
        invoice.setCustomer(customer);
        invoice.setInvoiceType(req.getInvoiceType());
        invoice.setExternalRef(req.getExternalRef());
        invoice.setAgentPrintedNet(req.getAgentPrintedNet());
        invoice.setPlasticDiscountPct(req.getPlasticDiscountPct());
        invoice.setPlasticDiscountAmount(req.getPlasticDiscountAmount());
        invoice.setPrintedBy(createdBy);
        invoice.setCreatedBy(createdBy);
        invoice.setSource(source);
        invoice.setImportBatchId(importBatchId);
        invoice.setBilledName(blankToNull(req.getBilledName()));

        // The flag is derived here rather than taken from the client: what matters to the
        // admin is that the invoice ended up against a different customer than the one the
        // import resolved, and only the saved customer can settle that.
        String resolved = blankToNull(req.getOriginalCustomerName());
        invoice.setOriginalCustomerName(resolved);
        if (resolved != null && !resolved.equalsIgnoreCase(customer.getName())) {
            invoice.setCustomerChanged(true);
            invoice.setCustomerChangedBy(createdBy);
        }

        // Rainco cash discount % from system settings (only for CASH Rainco)
        BigDecimal rainCoCashDiscPct = null;
        if (req.getInvoiceType() == InvoiceType.CASH) {
            rainCoCashDiscPct = settingsRepo.findByKey("rainco_cash_discount_pct")
                    .map(s -> new BigDecimal(s.getValue()))
                    .orElse(BigDecimal.ZERO);
            invoice.setCashDiscountPct(rainCoCashDiscPct);
        }
        if (req.getDiscountOverridePct() != null) {
            invoice.setDiscountOverridePct(req.getDiscountOverridePct());
            invoice.setDiscountOverrideBy(createdBy);
            invoice.setDiscountOverrideAt(LocalDateTime.now());
        }

        // Load and validate items before building any lines — block the whole invoice if any
        // line would oversell stock, rather than partially deducting then failing mid-way.
        Map<Long, Item> itemsById = new HashMap<>();

        List<String> stockErrors = new ArrayList<>();
        for (var lr : req.getLines()) {
            Item item = itemRepo.findById(lr.getItemId())
                    .orElseThrow(() -> new EntityNotFoundException("Item not found: " + lr.getItemId()));
            itemsById.put(item.getId(), item);
            guardFreeIssue(req.getMethod(), item, freeQtyOf(lr));
            // Free goods leave the warehouse too, so they count against available stock.
            int needed = lr.getQty() + freeQtyOf(lr);
            if (needed > item.getStockQty()) {
                stockErrors.add(item.getItemCode() + " (\"" + item.getDescription() + "\") — available "
                        + item.getStockQty() + ", requested " + needed
                        + (freeQtyOf(lr) > 0 ? " (" + lr.getQty() + " + " + freeQtyOf(lr) + " free)" : ""));
            }
        }
        if (!stockErrors.isEmpty()) {
            throw new IllegalArgumentException("Insufficient stock: " + String.join("; ", stockErrors));
        }

        // Build lines and accumulate brand-group totals
        Map<Long, BigDecimal> brandGroupValues = new LinkedHashMap<>();
        List<InvoiceLine> lines = new ArrayList<>();
        int sortIdx = 0;

        for (var lr : req.getLines()) {
            Item item = itemsById.get(lr.getItemId());

            BigDecimal wsp = engine.computeWsp(item);
            BigDecimal value = wsp.multiply(BigDecimal.valueOf(lr.getQty())).setScale(2, RoundingMode.HALF_UP);

            InvoiceLine line = new InvoiceLine();
            line.setInvoice(invoice);
            line.setItem(item);
            line.setBrand(item.getBrand());
            line.setQty(lr.getQty());
            line.setFreeQty(freeQtyOf(lr));
            line.setMrp(item.getMrp() != null ? item.getMrp() : BigDecimal.ZERO);
            line.setMarginPct(item.getMarginPct() != null ? item.getMarginPct()
                    : (item.getBrand().getDefaultMarginPct() != null ? item.getBrand().getDefaultMarginPct() : BigDecimal.ZERO));
            line.setWsp(wsp);
            line.setValue(value);
            line.setSortOrder(sortIdx++);

            brandGroupValues.merge(groupKey(item.getBrand()), value, BigDecimal::add);
            lines.add(line);
        }

        // Resolve slab pct per brand group (the plastic group's sentinel key has no matching
        // brand slabs, so it naturally resolves to 0% — plastic only gets the overall discount)
        Map<Long, BigDecimal> slabPcts = new HashMap<>();
        for (Map.Entry<Long, BigDecimal> e : brandGroupValues.entrySet()) {
            List<DiscountSlab> slabs = brandSlabs.getOrDefault(e.getKey(), List.of());
            slabPcts.put(e.getKey(), engine.findSlabDiscountPct(slabs, e.getValue()));
        }

        // The override has to land before the lines snapshot their rate, not after:
        // every later view recomputes the invoice from those snapshots, so a line
        // holding the slab rate would show the slab discount for good, whatever the
        // totals said at save time.
        applyDiscountOverride(slabPcts, req.getDiscountOverridePct());

        // Stamp applied discount pct onto each line
        for (InvoiceLine line : lines) {
            line.setAppliedDiscountPct(slabPcts.getOrDefault(groupKey(line.getBrand()), BigDecimal.ZERO));
        }

        // Which groups are Rainco. The engine works the cash discount out from these
        // itself, on the value left after their slab discount.

        Set<Long> raincoKeys = raincoGroupKeys(brandGroupValues, allBrands);

        DiscountEngineService.InvoiceTotals totals = engine.computeInvoiceTotals(
                brandGroupValues, slabPcts,
                req.getInvoiceType(),
                raincoKeys, PLASTIC_GROUP_KEY,
                rainCoCashDiscPct,
                req.getPlasticDiscountPct(), req.getPlasticDiscountAmount()
        );

        // Typed free issue is flagged for the admin's review queue, whoever entered it.
        if (lines.stream().anyMatch(l -> l.getFreeQty() != null && l.getFreeQty() > 0)) {
            invoice.setFreeIssueAddedBy(createdBy);
            invoice.setFreeIssueAddedAt(java.time.LocalDateTime.now());
        }

        // The invoice number IS the bill number — one string identifies the document in
        // both places, and its prefix says where it came from. An admin correcting it on
        // import supplies the finished number instead.
        String override = blankToNull(req.getInvoiceNoOverride());
        invoice.setInvoiceNo(override != null ? override
                : numbering.format(numbering.businessFor(req.getMethod()),
                                   req.getBillSource(), req.getBillNumber()));
        invoice.getLines().addAll(lines);
        invoiceRepo.save(invoice);

        // Raise the matching bill so the money can be collected through the existing
        // payment workflow. Same transaction on purpose: an invoice nobody can collect
        // against is worse than one that failed to save at all.
        var attachment = billBridge.raiseBill(invoice, totals.netTotal, createdBy,
                                              req.getDeliveryMode(), req.getDeliveryRunId());
        invoice.setBillId(attachment.bill().getId());
        invoice.setBillLinkedExisting(attachment.linked());
        invoiceRepo.save(invoice);

        // Reduce stock per line — paid and free together, since both physically leave.
        for (InvoiceLine line : lines) {
            int moved = line.getQty() + (line.getFreeQty() == null ? 0 : line.getFreeQty());
            if (moved == 0) continue;
            itemRepo.adjustStock(line.getItem().getId(), -moved);
            StockMovement mv = new StockMovement();
            mv.setItem(line.getItem());
            mv.setType(StockMovementType.INVOICE_DEDUCTION);
            mv.setQuantity(-moved);
            mv.setReferenceId(invoice.getId());
            mv.setReferenceType("INVOICE");
            movementRepo.save(mv);
        }

        return toResponse(invoice, totals);
    }

    @Transactional(readOnly = true)
    public InvoiceResponse getById(Long id) {
        Invoice invoice = invoiceRepo.findByIdWithDetails(id)
                .orElseThrow(() -> new EntityNotFoundException("Invoice not found: " + id));
        return toResponse(invoice, recomputeTotals(invoice));
    }

    @Transactional(readOnly = true)
    public Page<InvoiceSummaryResponse> search(InvoiceMethod method, LocalDate from, LocalDate to,
                                               String search, Pageable pageable) {
        return invoiceRepo.search(method, from, to, search, pageable)
                .map(i -> toSummary(i, recomputeTotals(i)));
    }

    @Transactional
    public InvoicePrintResponse printInvoice(Long id, String printedBy) {
        Invoice invoice = invoiceRepo.findByIdWithDetails(id)
                .orElseThrow(() -> new EntityNotFoundException("Invoice not found: " + id));

        String watermark = invoice.isDuplicatePrint() ? "DUPLICATE" : "ORIGINAL";

        if (!invoice.isDuplicatePrint()) {
            invoice.setDuplicatePrint(true);
            invoice.setPrintedBy(printedBy);
            invoiceRepo.save(invoice);
        }

        // Group lines by brand, preserving sort order
        Map<Long, InvoicePrintResponse.BrandGroup> groupMap = new LinkedHashMap<>();
        for (InvoiceLine line : invoice.getLines().stream()
                .sorted(Comparator.comparingInt(l -> l.getSortOrder() != null ? l.getSortOrder() : 0))
                .toList()) {

            Long brandId = groupKey(line.getBrand());
            boolean isPlastic = line.getBrand().getCategory() == CategoryType.PLASTIC;
            groupMap.computeIfAbsent(brandId, k -> {
                InvoicePrintResponse.BrandGroup g = new InvoicePrintResponse.BrandGroup();
                g.setBrandId(brandId);
                g.setBrandName(isPlastic ? "PLASTIC" : line.getBrand().getName());
                g.setLines(new ArrayList<>());
                g.setBrandTotalWsp(BigDecimal.ZERO);
                g.setBrandDiscountPct(line.getAppliedDiscountPct() != null
                        ? line.getAppliedDiscountPct() : BigDecimal.ZERO);
                return g;
            });

            InvoicePrintResponse.BrandGroup group = groupMap.get(brandId);

            // Compute free-issue qty for this line
            // A typed free quantity is what was actually given, so it wins over the
            // item's automatic buy-N-get-M scheme rather than being added to it.
            Integer freeQty = null;
            Item item = line.getItem();
            if (line.getFreeQty() != null && line.getFreeQty() > 0) {
                freeQty = line.getFreeQty();
            } else if (item.getFreeIssueBuyQty() != null && item.getFreeIssueFreeQty() != null
                    && item.getFreeIssueBuyQty() > 0
                    && line.getQty() >= item.getFreeIssueBuyQty()) {
                freeQty = (line.getQty() / item.getFreeIssueBuyQty()) * item.getFreeIssueFreeQty();
            }

            InvoicePrintResponse.PrintLine pl = new InvoicePrintResponse.PrintLine();
            pl.setItemCode(item.getItemCode());
            pl.setDescription(item.getDescription());
            pl.setQty(line.getQty());
            pl.setFreeIssueQty(freeQty);
            pl.setMrp(line.getMrp());
            pl.setMarginPct(line.getMarginPct());
            pl.setWsp(line.getWsp());
            pl.setValue(line.getValue());
            group.getLines().add(pl);
            group.setBrandTotalWsp(group.getBrandTotalWsp().add(line.getValue()));
        }

        // Compute per-brand discount amount and net
        for (InvoicePrintResponse.BrandGroup g : groupMap.values()) {
            BigDecimal discAmt = g.getBrandTotalWsp()
                    .multiply(g.getBrandDiscountPct())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            g.setBrandDiscountAmount(discAmt);
            g.setBrandNetTotal(g.getBrandTotalWsp().subtract(discAmt));
        }

        DiscountEngineService.InvoiceTotals totals = recomputeTotals(invoice);

        // Company header depends on invoice method
        String companyName, distributorLine;
        if (invoice.getMethod() == InvoiceMethod.STATIONERY_ONLY) {
            companyName = "GHANIM ENTERPRISES";
            distributorLine = "DISTRIBUTOR FOR Rainco Trading";
        } else {
            companyName = "GHANIM DISTRIBUTORS";
            distributorLine = "DISTRIBUTOR FOR Rainco (Pvt) Ltd";
        }

        InvoicePrintResponse resp = new InvoicePrintResponse();
        resp.setWatermark(watermark);
        resp.setCompanyName(companyName);
        resp.setCompanyAddress("18/1, UDAYARAJA MW");
        resp.setCompanyCity("BADULLA");
        resp.setCompanyTel("0552230297");
        resp.setDistributorLine(distributorLine);
        resp.setInvoiceNo(invoice.getInvoiceNo());
        resp.setExternalRef(invoice.getExternalRef());
        resp.setInvoiceDate(invoice.getInvoiceDate());
        resp.setMethod(invoice.getMethod());
        resp.setInvoiceType(invoice.getInvoiceType());
        resp.setCustomerCode(invoice.getCustomer().getCustomerCode());
        resp.setCustomerName(invoice.getCustomer().getName());
        resp.setCustomerAddress(resolveAddress(invoice.getCustomer()));
        resp.setCustomerPhone(invoice.getCustomer().getPhone());
        resp.setBrandGroups(new ArrayList<>(groupMap.values()));
        resp.setGrossTotal(totals.grossTotal);
        resp.setTotalSlabDiscount(totals.totalSlabDiscount);
        resp.setCashDiscountPct(invoice.getCashDiscountPct());
        resp.setCashDiscountAmount(totals.cashDiscountAmount);
        resp.setNetTotal(totals.netTotal);
        return resp;
    }


    // ---- pricing a draft ----

    /**
     * Prices a draft invoice without saving anything, so the discount is visible while it
     * is being built rather than only after it exists.
     *
     * Runs the same engine as {@link #create}: a second implementation in the browser
     * would drift from the real figure, and a quoted discount that doesn't match the
     * saved one is worse than no quote at all.
     */
    @Transactional(readOnly = true)
    public QuoteResponse quote(QuoteRequest req) {
        List<Brand> allBrands = brandRepo.findAllActiveWithSlabs();
        Map<Long, List<DiscountSlab>> brandSlabs = new HashMap<>();
        for (Brand b : allBrands) {
            brandSlabs.put(b.getId(), b.getSlabs().stream()
                    .sorted(Comparator.comparingInt(sl -> sl.getSortOrder() != null ? sl.getSortOrder() : 0))
                    .toList());
        }

        Map<Long, BigDecimal> groupValues = new LinkedHashMap<>();
        Map<Long, String> groupNames = new LinkedHashMap<>();
        int totalFree = 0;

        for (var lr : req.getLines()) {
            if (lr.getItemId() == null) continue;
            Item item = itemRepo.findById(lr.getItemId()).orElse(null);
            if (item == null) continue;

            int qty = lr.getQty() == null ? 0 : lr.getQty();
            totalFree += lr.getFreeQty() == null ? 0 : lr.getFreeQty();

            BigDecimal value = engine.computeWsp(item)
                    .multiply(BigDecimal.valueOf(qty))
                    .setScale(2, RoundingMode.HALF_UP);

            Long key = groupKey(item.getBrand());
            groupValues.merge(key, value, BigDecimal::add);
            groupNames.putIfAbsent(key,
                    item.getBrand().getCategory() == CategoryType.PLASTIC
                            ? "PLASTIC" : item.getBrand().getName());
        }

        Map<Long, BigDecimal> slabPcts = new HashMap<>();
        for (var e : groupValues.entrySet()) {
            slabPcts.put(e.getKey(),
                    engine.findSlabDiscountPct(brandSlabs.getOrDefault(e.getKey(), List.of()), e.getValue()));
        }

        BigDecimal cashDiscPct = null;
        if (req.getInvoiceType() == InvoiceType.CASH) {
            cashDiscPct = settingsRepo.findByKey("rainco_cash_discount_pct")
                    .map(v -> new BigDecimal(v.getValue()))
                    .orElse(BigDecimal.ZERO);
        }

        applyDiscountOverride(slabPcts, req.getDiscountOverridePct());
        Set<Long> raincoKeys = raincoGroupKeys(groupValues, allBrands);

        DiscountEngineService.InvoiceTotals totals = engine.computeInvoiceTotals(
                groupValues, slabPcts, req.getInvoiceType(),
                raincoKeys, PLASTIC_GROUP_KEY,
                cashDiscPct,
                req.getPlasticDiscountPct(), req.getPlasticDiscountAmount());

        List<QuoteResponse.BrandGroup> groups = new ArrayList<>();
        for (var e : groupValues.entrySet()) {
            BigDecimal gross = e.getValue();
            BigDecimal pct = slabPcts.getOrDefault(e.getKey(), BigDecimal.ZERO);
            BigDecimal amount = gross.multiply(pct)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

            // The next slab up, so it is obvious when a better rate is within reach.
            BigDecimal nextAt = null, nextPct = null, toGo = null;
            for (DiscountSlab slab : brandSlabs.getOrDefault(e.getKey(), List.of())) {
                BigDecimal from = slab.getMinValue();
                if (from == null || from.compareTo(gross) <= 0) continue;
                if (nextAt == null || from.compareTo(nextAt) < 0) {
                    nextAt = from;
                    nextPct = slab.getDiscountPct();
                    toGo = from.subtract(gross);
                }
            }

            groups.add(QuoteResponse.BrandGroup.builder()
                    .brandId(e.getKey())
                    .brandName(groupNames.get(e.getKey()))
                    .gross(gross)
                    .discountPct(pct)
                    .discountAmount(amount)
                    .net(gross.subtract(amount))
                    .nextSlabAt(nextAt)
                    .nextSlabPct(nextPct)
                    .amountToNextSlab(toGo)
                    .build());
        }
        groups.sort(Comparator.comparing(QuoteResponse.BrandGroup::getBrandName));

        BigDecimal allDiscounts = totals.totalSlabDiscount
                .add(totals.cashDiscountAmount)
                .add(totals.plasticDiscount);

        return QuoteResponse.builder()
                .brandGroups(groups)
                .grossTotal(totals.grossTotal)
                .totalSlabDiscount(totals.totalSlabDiscount)
                .cashDiscountPct(cashDiscPct)
                .cashDiscountAmount(totals.cashDiscountAmount)
                .plasticDiscount(totals.plasticDiscount)
                .netTotal(totals.netTotal)
                .totalDiscount(allDiscounts)
                .totalFreeQty(totalFree)
                .build();
    }

    // ---- admin edit ----

    /**
     * Rewrites an invoice: its lines, quantities, free quantities, discounts, customer
     * and dates. Admin only.
     *
     * Stock moves by the difference, not by the new figures — an edit that changes 10 to
     * 12 must take 2 more, not another 12. The linked bill's total is rewritten to match,
     * because the bill is what gets collected and a stale total would be collected wrongly.
     *
     * The invoice number is deliberately not editable: it is also the bill number, and the
     * two must stay in step.
     */
    @Transactional
    public InvoiceResponse update(Long id, InvoiceRequest req, String editedBy) {
        Invoice invoice = invoiceRepo.findByIdWithDetails(id)
                .orElseThrow(() -> new EntityNotFoundException("Invoice not found: " + id));

        Customer customer = customerRepo.findById(req.getCustomerId())
                .orElseThrow(() -> new EntityNotFoundException("Customer not found"));

        // What the current lines are holding, so the delta can be worked out per item.
        Map<Long, Integer> before = new HashMap<>();
        for (InvoiceLine l : invoice.getLines()) {
            before.merge(l.getItem().getId(),
                    l.getQty() + (l.getFreeQty() == null ? 0 : l.getFreeQty()), Integer::sum);
        }

        Map<Long, Item> itemsById = new HashMap<>();
        Map<Long, Integer> after = new HashMap<>();
        for (var lr : req.getLines()) {
            Item item = itemRepo.findById(lr.getItemId())
                    .orElseThrow(() -> new EntityNotFoundException("Item not found: " + lr.getItemId()));
            itemsById.put(item.getId(), item);
            guardFreeIssue(invoice.getMethod(), item, freeQtyOf(lr));
            after.merge(item.getId(), lr.getQty() + freeQtyOf(lr), Integer::sum);
        }

        // Only an increase can oversell. Validate every one before moving anything.
        List<String> stockErrors = new ArrayList<>();
        for (var e : after.entrySet()) {
            int delta = e.getValue() - before.getOrDefault(e.getKey(), 0);
            if (delta <= 0) continue;
            Item item = itemsById.get(e.getKey());
            if (delta > item.getStockQty()) {
                stockErrors.add(item.getItemCode() + " (\"" + item.getDescription() + "\") — "
                        + delta + " more needed, only " + item.getStockQty() + " available");
            }
        }
        if (!stockErrors.isEmpty()) {
            throw new IllegalArgumentException("Insufficient stock: " + String.join("; ", stockErrors));
        }

        invoice.setInvoiceDate(req.getInvoiceDate());
        invoice.setCustomer(customer);
        invoice.setInvoiceType(req.getInvoiceType());
        invoice.setPlasticDiscountPct(req.getPlasticDiscountPct());
        invoice.setPlasticDiscountAmount(req.getPlasticDiscountAmount());
        invoice.setAgentPrintedNet(req.getAgentPrintedNet());
        if (req.getExternalRef() != null) invoice.setExternalRef(blankToNull(req.getExternalRef()));

        BigDecimal cashDiscPct = null;
        if (req.getInvoiceType() == InvoiceType.CASH) {
            cashDiscPct = settingsRepo.findByKey("rainco_cash_discount_pct")
                    .map(s -> new BigDecimal(s.getValue()))
                    .orElse(BigDecimal.ZERO);
        }
        invoice.setCashDiscountPct(cashDiscPct);

        List<Brand> allBrands = brandRepo.findAllActiveWithSlabs();
        Map<Long, List<DiscountSlab>> brandSlabs = new HashMap<>();
        for (Brand b : allBrands) {
            brandSlabs.put(b.getId(), b.getSlabs().stream()
                    .sorted(Comparator.comparingInt(s -> s.getSortOrder() != null ? s.getSortOrder() : 0))
                    .toList());
        }

        Map<Long, BigDecimal> brandGroupValues = new LinkedHashMap<>();
        List<InvoiceLine> lines = new ArrayList<>();
        int sortIdx = 0;
        for (var lr : req.getLines()) {
            Item item = itemsById.get(lr.getItemId());
            BigDecimal wsp = engine.computeWsp(item);
            BigDecimal value = wsp.multiply(BigDecimal.valueOf(lr.getQty())).setScale(2, RoundingMode.HALF_UP);

            InvoiceLine line = new InvoiceLine();
            line.setInvoice(invoice);
            line.setItem(item);
            line.setBrand(item.getBrand());
            line.setQty(lr.getQty());
            line.setFreeQty(freeQtyOf(lr));
            line.setMrp(item.getMrp() != null ? item.getMrp() : BigDecimal.ZERO);
            line.setMarginPct(item.getMarginPct() != null ? item.getMarginPct()
                    : (item.getBrand().getDefaultMarginPct() != null
                        ? item.getBrand().getDefaultMarginPct() : BigDecimal.ZERO));
            line.setWsp(wsp);
            line.setValue(value);
            line.setSortOrder(sortIdx++);
            brandGroupValues.merge(groupKey(item.getBrand()), value, BigDecimal::add);
            lines.add(line);
        }

        Map<Long, BigDecimal> slabPcts = new HashMap<>();
        for (var e : brandGroupValues.entrySet()) {
            slabPcts.put(e.getKey(),
                    engine.findSlabDiscountPct(brandSlabs.getOrDefault(e.getKey(), List.of()), e.getValue()));
        }
        // Same ordering as create: the snapshot is what every later read works from.
        applyDiscountOverride(slabPcts, req.getDiscountOverridePct());

        for (InvoiceLine line : lines) {
            line.setAppliedDiscountPct(slabPcts.getOrDefault(groupKey(line.getBrand()), BigDecimal.ZERO));
        }

        Set<Long> raincoKeys = raincoGroupKeys(brandGroupValues, allBrands);

        DiscountEngineService.InvoiceTotals totals = engine.computeInvoiceTotals(
                brandGroupValues, slabPcts, req.getInvoiceType(),
                raincoKeys, PLASTIC_GROUP_KEY,
                cashDiscPct,
                req.getPlasticDiscountPct(), req.getPlasticDiscountAmount());

        invoice.getLines().clear();
        invoice.getLines().addAll(lines);

        if (lines.stream().anyMatch(l -> l.getFreeQty() != null && l.getFreeQty() > 0)) {
            if (invoice.getFreeIssueAddedBy() == null) {
                invoice.setFreeIssueAddedBy(editedBy);
                invoice.setFreeIssueAddedAt(java.time.LocalDateTime.now());
            }
        } else {
            invoice.setFreeIssueAddedBy(null);
            invoice.setFreeIssueAddedAt(null);
        }

        invoice.setEditedBy(editedBy);
        invoice.setEditedAt(java.time.LocalDateTime.now());
        // An edited invoice goes back in front of the admin.
        invoice.setReviewed(false);
        invoice.setReviewedBy(null);
        invoice.setReviewedAt(null);
        invoiceRepo.save(invoice);

        // Move stock by the difference only.
        Set<Long> touched = new HashSet<>(before.keySet());
        touched.addAll(after.keySet());
        for (Long itemId : touched) {
            int delta = after.getOrDefault(itemId, 0) - before.getOrDefault(itemId, 0);
            if (delta == 0) continue;
            itemRepo.adjustStock(itemId, -delta);
            StockMovement mv = new StockMovement();
            mv.setItem(itemsById.containsKey(itemId)
                    ? itemsById.get(itemId)
                    : itemRepo.findById(itemId).orElseThrow());
            mv.setType(StockMovementType.INVOICE_DEDUCTION);
            mv.setQuantity(-delta);
            mv.setReferenceId(invoice.getId());
            mv.setReferenceType("INVOICE_EDIT");
            mv.setNotes("Edited by " + editedBy);
            movementRepo.save(mv);
        }

        billBridge.syncBillTotal(invoice, totals.netTotal);
        return toResponse(invoice, totals);
    }

    // ---- admin review ----

    /**
     * The admin's queue of newly entered invoices. Defaults to what has not been looked at
     * yet; {@code changedOnly} narrows it to invoices whose customer was redirected away
     * from the name they were billed under.
     */
    @Transactional(readOnly = true)
    public Page<InvoiceSummaryResponse> reviewQueue(Boolean reviewed, InvoiceSource source,
                                                    boolean changedOnly, LocalDate from, LocalDate to,
                                                    String search, Pageable pageable) {
        return invoiceRepo.reviewSearch(reviewed, source, changedOnly, from, to, search, pageable)
                .map(i -> toSummary(i, recomputeTotals(i)));
    }

    @Transactional(readOnly = true)
    public long pendingReviewCount() {
        return invoiceRepo.countByReviewedFalse();
    }

    @Transactional
    public InvoiceSummaryResponse setReviewed(Long id, boolean reviewed, String by) {
        Invoice inv = invoiceRepo.findByIdWithDetails(id)
                .orElseThrow(() -> new EntityNotFoundException("Invoice not found: " + id));
        applyReview(inv, reviewed, by);
        invoiceRepo.save(inv);
        return toSummary(inv, recomputeTotals(inv));
    }

    @Transactional
    public int setReviewedBulk(List<Long> ids, boolean reviewed, String by) {
        if (ids == null || ids.isEmpty()) return 0;
        List<Invoice> found = invoiceRepo.findAllById(ids);
        for (Invoice inv : found) applyReview(inv, reviewed, by);
        invoiceRepo.saveAll(found);
        return found.size();
    }

    private void applyReview(Invoice inv, boolean reviewed, String by) {
        inv.setReviewed(reviewed);
        inv.setReviewedBy(reviewed ? by : null);
        inv.setReviewedAt(reviewed ? java.time.LocalDateTime.now() : null);
    }

    // ---- helpers ----

    private int freeQtyOf(com.multi.finance.invoicing.dto.request.InvoiceLineRequest lr) {
        return lr.getFreeQty() == null ? 0 : lr.getFreeQty();
    }

    /**
     * Refuses a free quantity on anything that is never given away.
     *
     * <p>Judged per line against {@link FreeIssuePolicy}: stationery-only invoices, and
     * within them only shoe polish and the K01047 umbrella. The quantity itself is
     * whatever the rep said once the bill was totalled — this only checks it is being
     * typed somewhere it belongs.
     */
    private void guardFreeIssue(InvoiceMethod method, Item item, int freeQty) {
        if (freeQty <= 0) return;
        if (!freeIssuePolicy.allowsFreeQty(method, item)) {
            throw new IllegalArgumentException(freeIssuePolicy.refusalReason(method, item));
        }
    }


    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private String resolveAddress(Customer customer) {
        String address = customer.getAddress();
        return (address != null && !address.isBlank()) ? address : customer.getArea();
    }

    private boolean isRaincoBrand(List<Brand> brands, Long brandId) {
        return brands.stream().anyMatch(b -> b.getId().equals(brandId) && b.getCategory() == CategoryType.RAINCO);
    }

    /**
     * Replaces every group's slab rate with a flat admin rate, when one was given.
     *
     * <p>Applied to the rate map before the engine runs, so the override flows through
     * the totals and onto each line's {@code appliedDiscountPct} exactly as a slab
     * would. Nothing downstream — returns, the variance check, a later recompute —
     * needs to know it was an override rather than a band.
     */
    private void applyDiscountOverride(Map<Long, BigDecimal> slabPcts, BigDecimal overridePct) {
        if (overridePct == null || overridePct.compareTo(BigDecimal.ZERO) < 0) return;
        slabPcts.replaceAll((k, v) -> overridePct);
    }

    /** The brand-group keys the cash discount applies to. */
    private Set<Long> raincoGroupKeys(Map<Long, BigDecimal> groupValues, List<Brand> brands) {
        return groupValues.keySet().stream()
                .filter(k -> isRaincoBrand(brands, k))
                .collect(java.util.stream.Collectors.toSet());
    }

    private DiscountEngineService.InvoiceTotals recomputeTotals(Invoice invoice) {
        Map<Long, BigDecimal> groupValues = new LinkedHashMap<>();
        Map<Long, BigDecimal> slabPcts = new LinkedHashMap<>();
        Set<Long> raincoKeys = new java.util.HashSet<>();

        for (InvoiceLine line : invoice.getLines()) {
            Long key = groupKey(line.getBrand());
            groupValues.merge(key, line.getValue(), BigDecimal::add);
            slabPcts.put(key,
                    line.getAppliedDiscountPct() != null ? line.getAppliedDiscountPct() : BigDecimal.ZERO);
            if (line.getBrand().getCategory() == CategoryType.RAINCO) {
                raincoKeys.add(key);
            }
        }

        return engine.computeInvoiceTotals(
                groupValues, slabPcts,
                invoice.getInvoiceType(),
                raincoKeys, PLASTIC_GROUP_KEY,
                invoice.getCashDiscountPct(),
                invoice.getPlasticDiscountPct(), invoice.getPlasticDiscountAmount()
        );
    }

    private InvoiceResponse toResponse(Invoice inv, DiscountEngineService.InvoiceTotals t) {
        InvoiceResponse r = new InvoiceResponse();
        r.setId(inv.getId());
        r.setInvoiceNo(inv.getInvoiceNo());
        r.setExternalRef(inv.getExternalRef());
        r.setMethod(inv.getMethod());
        r.setInvoiceDate(inv.getInvoiceDate());
        r.setCustomerId(inv.getCustomer().getId());
        r.setCustomerName(inv.getCustomer().getName());
        r.setCustomerAddress(resolveAddress(inv.getCustomer()));
        r.setInvoiceType(inv.getInvoiceType());
        r.setGrossTotal(t.grossTotal);
        r.setTotalSlabDiscount(t.totalSlabDiscount);
        r.setDiscountOverridePct(inv.getDiscountOverridePct());
        r.setDiscountOverrideBy(inv.getDiscountOverrideBy());
        r.setCashDiscountPct(inv.getCashDiscountPct());
        r.setCashDiscountAmount(t.cashDiscountAmount);
        r.setPlasticDiscountPct(inv.getPlasticDiscountPct());
        r.setPlasticDiscountAmount(inv.getPlasticDiscountAmount());
        r.setNetTotal(t.netTotal);
        r.setAgentPrintedNet(inv.getAgentPrintedNet());
        if (inv.getAgentPrintedNet() != null) {
            r.setVariance(inv.getAgentPrintedNet().subtract(t.netTotal));
        }
        r.setPrintedBy(inv.getPrintedBy());
        r.setCreatedAt(inv.getCreatedAt());
        r.setDuplicatePrint(inv.isDuplicatePrint());
        r.setBillId(inv.getBillId());
        r.setBillLinkedExisting(inv.isBillLinkedExisting());

        List<InvoiceLineResponse> lineResponses = inv.getLines().stream()
                .sorted(Comparator.comparingInt(l -> l.getSortOrder() != null ? l.getSortOrder() : 0))
                .map(l -> {
                    InvoiceLineResponse lr = new InvoiceLineResponse();
                    lr.setId(l.getId());
                    lr.setItemId(l.getItem().getId());
                    lr.setItemCode(l.getItem().getItemCode());
                    lr.setItemDescription(l.getItem().getDescription());
                    lr.setBrandId(l.getBrand().getId());
                    lr.setBrandName(l.getBrand().getName());
                    lr.setQty(l.getQty());
                    lr.setFreeQty(l.getFreeQty());
                    lr.setMrp(l.getMrp());
                    lr.setMarginPct(l.getMarginPct());
                    lr.setWsp(l.getWsp());
                    lr.setValue(l.getValue());
                    lr.setAppliedDiscountPct(l.getAppliedDiscountPct());
                    lr.setSortOrder(l.getSortOrder());
                    return lr;
                }).toList();
        r.setLines(lineResponses);
        return r;
    }

    private InvoiceSummaryResponse toSummary(Invoice inv, DiscountEngineService.InvoiceTotals t) {
        InvoiceSummaryResponse s = new InvoiceSummaryResponse();
        s.setId(inv.getId());
        s.setInvoiceNo(inv.getInvoiceNo());
        s.setExternalRef(inv.getExternalRef());
        s.setMethod(inv.getMethod());
        s.setInvoiceDate(inv.getInvoiceDate());
        s.setCustomerName(inv.getCustomer().getName());
        s.setInvoiceType(inv.getInvoiceType());
        s.setGrossTotal(t.grossTotal);
        s.setTotalDiscount(t.totalSlabDiscount.add(t.cashDiscountAmount).add(t.plasticDiscount));
        s.setCashDiscountAmount(t.cashDiscountAmount);
        s.setDiscountOverridePct(inv.getDiscountOverridePct());
        s.setDiscountOverrideBy(inv.getDiscountOverrideBy());
        s.setNetTotal(t.netTotal);
        s.setDuplicatePrint(inv.isDuplicatePrint());
        s.setFreeUmbrellaQty(inv.getLines().stream()
                .filter(l -> freeIssuePolicy.isFreeUmbrella(l.getItem()))
                .mapToInt(l -> l.getFreeQty() == null ? 0 : l.getFreeQty())
                .sum());
        s.setTotalFreeQty(inv.getLines().stream()
                .mapToInt(l -> l.getFreeQty() == null ? 0 : l.getFreeQty())
                .sum());
        s.setBillId(inv.getBillId());
        s.setBillLinkedExisting(inv.isBillLinkedExisting());
        s.setBilledName(inv.getBilledName());
        s.setOriginalCustomerName(inv.getOriginalCustomerName());
        s.setCustomerChanged(inv.isCustomerChanged());
        s.setCustomerChangedBy(inv.getCustomerChangedBy());
        s.setSource(inv.getSource());
        s.setCreatedBy(inv.getCreatedBy());
        s.setCreatedAt(inv.getCreatedAt());
        s.setFreeIssueAddedBy(inv.getFreeIssueAddedBy());
        s.setFreeIssueAddedAt(inv.getFreeIssueAddedAt());
        s.setEditedBy(inv.getEditedBy());
        s.setEditedAt(inv.getEditedAt());
        s.setReviewed(inv.isReviewed());
        s.setReviewedBy(inv.getReviewedBy());
        s.setReviewedAt(inv.getReviewedAt());
        return s;
    }
}
