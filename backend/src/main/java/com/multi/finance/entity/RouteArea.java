package com.multi.finance.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * One of the recurring lorry rounds — Bandarawela, Badulla, Haputale.
 *
 * <p>A managed list rather than typed each time: the counts an admin checks a round
 * against are worthless if the same route exists under three spellings.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "route_areas")
public class RouteArea {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    /** Lets the admin order the list the way the rounds are actually run. */
    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
