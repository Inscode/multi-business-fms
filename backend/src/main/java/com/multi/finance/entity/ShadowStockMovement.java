package com.multi.finance.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "shadow_stock_movements")
public class ShadowStockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private ReturnProduct product;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MovementType type; // STOCK_IN, BILL_OUT, SALABLE_RETURN, DAMAGE_IN, DAMAGE_TO_COMPANY

    @Column(nullable = false)
    private Long quantity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bill_id")
    private Bill bill; // nullable - for stock in, returns, etc.

    @Column(name = "invoice_number")
    private String invoiceNumber; // for system bill batch reference

    @Column(name = "movement_date", nullable = false)
    private LocalDate movementDate;

    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entered_by", nullable = false)
    private User enteredBy;

    @Column(name = "cancelled", nullable = false)
    @Builder.Default
    private Boolean cancelled = false; // for linked bills - mark deduction as cancelled

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum MovementType {
        STOCK_IN,           // Opening stock or new load
        BILL_OUT,           // Bill deduction
        SALABLE_RETURN,     // Salable return approved
        DAMAGE_IN,          // Damage return approved
        DAMAGE_TO_COMPANY   // Damage return given back to company
    }
}
