package com.multi.finance.invoicing.service;

import com.multi.finance.invoicing.dto.request.InvoiceRequest;
import com.multi.finance.invoicing.dto.response.InvoiceLineResponse;
import com.multi.finance.invoicing.dto.response.InvoicePrintResponse;
import com.multi.finance.invoicing.dto.response.InvoiceResponse;
import com.multi.finance.invoicing.dto.response.InvoiceSummaryResponse;
import com.multi.finance.invoicing.entity.*;
import com.multi.finance.entity.Customer;
import com.multi.finance.invoicing.enums.CategoryType;
import com.multi.finance.invoicing.enums.InvoiceMethod;
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
    private final CustomerRepository customerRepo;
    private final ItemRepository itemRepo;
    private final BrandRepository brandRepo;
    private final DiscountSlabRepository slabRepo;
    private final StockMovementRepository movementRepo;
    private final SystemSettingsRepository settingsRepo;
    private final DiscountEngineService engine = new DiscountEngineService();

    // Sentinel grouping key: all PLASTIC items are combined into one group (no per-brand/supplier
    // slab discount) since plastic only gets a single overall discount on the combined total.
    private static final Long PLASTIC_GROUP_KEY = -1L;

    private Long groupKey(Brand brand) {
        return brand.getCategory() == CategoryType.PLASTIC ? PLASTIC_GROUP_KEY : brand.getId();
    }

    @Transactional
    public InvoiceResponse create(InvoiceRequest req, String createdBy) {
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

        // Rainco cash discount % from system settings (only for CASH Rainco)
        BigDecimal rainCoCashDiscPct = null;
        if (req.getInvoiceType() == InvoiceType.CASH) {
            rainCoCashDiscPct = settingsRepo.findByKey("rainco_cash_discount_pct")
                    .map(s -> new BigDecimal(s.getValue()))
                    .orElse(BigDecimal.ZERO);
            invoice.setCashDiscountPct(rainCoCashDiscPct);
        }

        // Load and validate items before building any lines — block the whole invoice if any
        // line would oversell stock, rather than partially deducting then failing mid-way.
        Map<Long, Item> itemsById = new HashMap<>();
        List<String> stockErrors = new ArrayList<>();
        for (var lr : req.getLines()) {
            Item item = itemRepo.findById(lr.getItemId())
                    .orElseThrow(() -> new EntityNotFoundException("Item not found: " + lr.getItemId()));
            itemsById.put(item.getId(), item);
            if (lr.getQty() > item.getStockQty()) {
                stockErrors.add(item.getItemCode() + " (\"" + item.getDescription() + "\") — available "
                        + item.getStockQty() + ", requested " + lr.getQty());
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

        // Stamp applied discount pct onto each line
        for (InvoiceLine line : lines) {
            line.setAppliedDiscountPct(slabPcts.getOrDefault(groupKey(line.getBrand()), BigDecimal.ZERO));
        }

        // Compute Rainco gross (for cash discount)
        BigDecimal raincoGross = brandGroupValues.entrySet().stream()
                .filter(e -> isRaincoBrand(allBrands, e.getKey()))
                .map(Map.Entry::getValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal plasticGross = brandGroupValues.getOrDefault(PLASTIC_GROUP_KEY, BigDecimal.ZERO);

        DiscountEngineService.InvoiceTotals totals = engine.computeInvoiceTotals(
                brandGroupValues, slabPcts,
                req.getInvoiceType(),
                raincoGross, rainCoCashDiscPct,
                req.getPlasticDiscountPct(), req.getPlasticDiscountAmount(),
                plasticGross.compareTo(BigDecimal.ZERO) > 0 ? plasticGross : null
        );

        // Generate invoice number
        invoice.setInvoiceNo(generateInvoiceNo(req.getMethod()));
        invoice.getLines().addAll(lines);
        invoiceRepo.save(invoice);

        // Reduce stock per line
        for (InvoiceLine line : lines) {
            itemRepo.adjustStock(line.getItem().getId(), -line.getQty());
            StockMovement mv = new StockMovement();
            mv.setItem(line.getItem());
            mv.setType(StockMovementType.INVOICE_DEDUCTION);
            mv.setQuantity(-line.getQty());
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
            Integer freeQty = null;
            Item item = line.getItem();
            if (item.getFreeIssueBuyQty() != null && item.getFreeIssueFreeQty() != null
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

    // ---- helpers ----

    private String resolveAddress(Customer customer) {
        String address = customer.getAddress();
        return (address != null && !address.isBlank()) ? address : customer.getArea();
    }

    private String generateInvoiceNo(InvoiceMethod method) {
        String prefix = switch (method) {
            case MIX -> "GD-MX";
            case RAINCO_ONLY -> "GD-RC";
            case STATIONERY_ONLY -> "GD-ST";
        };
        String seqName = switch (method) {
            case MIX -> "seq_inv_mix";
            case RAINCO_ONLY -> "seq_inv_rc";
            case STATIONERY_ONLY -> "seq_inv_st";
        };
        Long seq = invoiceRepo.nextSequenceValue(seqName);
        return String.format("%s-%d", prefix, seq);
    }

    private boolean isRaincoBrand(List<Brand> brands, Long brandId) {
        return brands.stream().anyMatch(b -> b.getId().equals(brandId) && b.getCategory() == CategoryType.RAINCO);
    }

    private DiscountEngineService.InvoiceTotals recomputeTotals(Invoice invoice) {
        Map<Long, BigDecimal> groupValues = new LinkedHashMap<>();
        Map<Long, BigDecimal> slabPcts = new LinkedHashMap<>();
        BigDecimal raincoGross = BigDecimal.ZERO;
        BigDecimal plasticGross = BigDecimal.ZERO;

        for (InvoiceLine line : invoice.getLines()) {
            Long key = groupKey(line.getBrand());
            groupValues.merge(key, line.getValue(), BigDecimal::add);
            slabPcts.put(key,
                    line.getAppliedDiscountPct() != null ? line.getAppliedDiscountPct() : BigDecimal.ZERO);
            if (line.getBrand().getCategory() == CategoryType.RAINCO) {
                raincoGross = raincoGross.add(line.getValue());
            }
            if (line.getBrand().getCategory() == CategoryType.PLASTIC) {
                plasticGross = plasticGross.add(line.getValue());
            }
        }

        return engine.computeInvoiceTotals(
                groupValues, slabPcts,
                invoice.getInvoiceType(),
                raincoGross, invoice.getCashDiscountPct(),
                invoice.getPlasticDiscountPct(), invoice.getPlasticDiscountAmount(),
                plasticGross.compareTo(BigDecimal.ZERO) > 0 ? plasticGross : null
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
        s.setNetTotal(t.netTotal);
        s.setDuplicatePrint(inv.isDuplicatePrint());
        return s;
    }
}
