package com.multi.finance.invoicing.entity;

import com.multi.finance.invoicing.enums.CategoryType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * One press of Import. Groups the invoices that came off a single file so their
 * products can be totalled and checked against the agent's summary bill.
 */
@Entity
@Table(name = "inv_import_batches")
@Getter @Setter
public class ImportBatch {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CategoryType category;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "imported_by")
    private String importedBy;

    @Column(name = "imported_at", nullable = false)
    private LocalDateTime importedAt = LocalDateTime.now();

    @Column(name = "invoice_count", nullable = false)
    private Integer invoiceCount = 0;
}
