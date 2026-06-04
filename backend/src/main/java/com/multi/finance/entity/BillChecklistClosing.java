package com.multi.finance.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "bill_checklist_closings",
       uniqueConstraints = @UniqueConstraint(columnNames = {"item_name", "closing_date"}))
public class BillChecklistClosing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "item_name", nullable = false)
    private String itemName;

    @Column(name = "closing_date", nullable = false)
    private LocalDate closingDate;

    @Column(name = "marked_entered", nullable = false)
    private Integer markedEntered;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "closed_by_id", nullable = false)
    private User closedBy;

    @Column(name = "closed_at", nullable = false)
    private LocalDateTime closedAt;
}
