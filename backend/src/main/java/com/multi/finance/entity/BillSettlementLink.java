package com.multi.finance.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * One hand-written bill collecting part of a system bill's money.
 *
 * <p>A rep on a round writes a bill at each shop while the system raises one covering the
 * load, so a system bill's money often arrives on several hand-written ones. Each of them
 * is a row here.
 *
 * <p>Not the same as {@code BillStockLink}, which ties a system bill to many manual ones
 * for end-of-month stock. That is about goods; this is about money.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "bill_settlement_links")
public class BillSettlementLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The bill whose money is collected elsewhere. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "system_bill_id", nullable = false)
    private Bill systemBill;

    /**
     * The hand-written bill collecting it. Unique across the table: one manual bill
     * settles one system bill, or the same money would close two of them.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "manual_bill_id", nullable = false)
    private Bill manualBill;

    @Column(length = 300)
    private String note;

    @Column(name = "linked_by", length = 100)
    private String linkedBy;

    @Column(name = "linked_at", nullable = false)
    @Builder.Default
    private LocalDateTime linkedAt = LocalDateTime.now();
}
