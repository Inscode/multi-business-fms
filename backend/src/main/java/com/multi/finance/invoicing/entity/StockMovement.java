package com.multi.finance.invoicing.entity;

import com.multi.finance.invoicing.enums.StockMovementType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "inv_stock_movements")
@Getter @Setter
public class StockMovement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StockMovementType type;

    // Positive = stock in (return salable), Negative = stock out (invoice / damage)
    @Column(nullable = false)
    private Integer quantity;

    // Optional reference (invoice_id or return_id)
    @Column(name = "reference_id")
    private Long referenceId;

    @Column(name = "reference_type", length = 50)
    private String referenceType;

    private String notes;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
