package com.multi.finance.entity;

import com.multi.finance.enums.ReturnType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A photograph evidencing a return.
 *
 * <p>Owned by exactly one of two things, because returns are counted two ways. A store
 * pickup or immediate delivery is a single shop, so its photo hangs off the return. A
 * route round is checked the morning after the lorry is back, and fifteen shops' returns
 * are written down the same page of a book — that page evidences the whole round, so it
 * hangs off the run, and there is rarely only one of them.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "return_images")
public class ReturnImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Set for a pickup or immediate return — the photo of that one shop's goods. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bill_return_id")
    private BillReturn billReturn;

    /** Set for a route round — a page of the book covering every return on it. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_run_id")
    private DeliveryRun deliveryRun;

    /**
     * Damage and salable are written in separate books and settled separately, so a
     * page is always one or the other.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "return_type", nullable = false)
    private ReturnType returnType;

    /** Which page of the book, when the accountant numbers them. */
    @Column(name = "page_no")
    private Integer pageNo;

    @Column(name = "image_url", nullable = false, columnDefinition = "TEXT")
    private String imageUrl;

    @Column(name = "uploaded_by", length = 100)
    private String uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    @Builder.Default
    private LocalDateTime uploadedAt = LocalDateTime.now();
}
