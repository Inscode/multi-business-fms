package com.multi.finance.invoicing.entity;

import com.multi.finance.invoicing.enums.InvoiceMethod;
import com.multi.finance.invoicing.enums.InvoiceType;
import com.multi.finance.entity.Customer;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "inv_invoices",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_external_ref_method",
        columnNames = {"external_ref", "method"}
    ))
@Getter @Setter
public class Invoice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // System-generated, method-prefixed: GD-MX-000001 / GD-RC-000001 / GD-ST-000001
    @Column(name = "invoice_no", nullable = false, unique = true)
    private String invoiceNo;

    // Agent's own invoice number — only for RAINCO_ONLY / STATIONERY_ONLY
    @Column(name = "external_ref")
    private String externalRef;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvoiceMethod method;

    @Column(name = "invoice_date", nullable = false)
    private LocalDate invoiceDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(name = "invoice_type", nullable = false)
    private InvoiceType invoiceType;

    // 5% for Rainco CASH invoices — stored for audit, sourced from system settings at save time
    @Column(name = "cash_discount_pct", precision = 5, scale = 2)
    private BigDecimal cashDiscountPct;

    // Plastic-only manual discounts (applied after Σ lines)
    @Column(name = "plastic_discount_pct", precision = 5, scale = 2)
    private BigDecimal plasticDiscountPct;

    @Column(name = "plastic_discount_amount", precision = 12, scale = 2)
    private BigDecimal plasticDiscountAmount;

    // Agent's printed net — for cross-check on RAINCO_ONLY / STATIONERY_ONLY
    @Column(name = "agent_printed_net", precision = 12, scale = 2)
    private BigDecimal agentPrintedNet;

    @Column(name = "printed_by")
    private String printedBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "is_duplicate_print")
    private boolean duplicatePrint = false;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InvoiceLine> lines = new ArrayList<>();
}
