package com.multi.finance.entity;

import com.multi.finance.enums.BillSource;
import com.multi.finance.enums.BillStatus;
import com.multi.finance.enums.BillType;
import com.multi.finance.enums.BusinessType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Fetch;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "bills")
public class Bill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bill_number")
    private String billNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "business", nullable = false)
    private BusinessType business;

    @Column(name = "division", nullable = false)
    private String division;

    @Enumerated(EnumType.STRING)
    @Column(name = "bill_source", nullable = false)
    private BillSource billSource;

    @Enumerated(EnumType.STRING)
    @Column(name = "bill_type", nullable = false)
    private BillType billType;

    @Column(name = "customer_name", nullable = false)
    private String customerName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount;

    @Column(name = "area")
    private String area;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BillStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_holder_id")
    private Worker currentHolder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entered_by", nullable = false)
    private User enteredBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "received_by")
    private User receivedBy;

    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    @Column(name = "bill_date" ,nullable = false)
    private LocalDate billDate;

    private String notes;

    @Column(name = "amount_paid", nullable = false)
    private BigDecimal amountPaid;

    @Column(name = "balance_remaining", nullable = false)
    private BigDecimal balanceRemaining;

    @Column(name = "fully_paid", nullable = false)
    private Boolean fullyPaid;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
