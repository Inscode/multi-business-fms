package com.multi.finance.entity;

import com.multi.finance.enums.DeliveryRunStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One lorry round: an area, the day it goes out, and the bills that went with it.
 *
 * <p>The reason this is a record rather than a filter over area and date is the
 * question it has to answer — "eighteen bills went to Bandarawela on the 17th, to
 * fourteen customers, worth this much". Reconstructing that from bill fields would
 * silently include a bill that happened to share the area and date but travelled some
 * other way.
 *
 * <p>A run covers every business, because one lorry carries all of them.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "delivery_runs")
public class DeliveryRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The rounds this trip covers. Often more than one — a lorry doing Bandarawela,
     * Haputale and Diyatalawa is one load with one set of counts, not three.
     */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "delivery_run_areas",
               joinColumns = @JoinColumn(name = "run_id"),
               inverseJoinColumns = @JoinColumn(name = "route_area_id"))
    @Builder.Default
    private java.util.Set<RouteArea> areas = new java.util.LinkedHashSet<>();

    /** The areas as one label, for a heading or a chip: "Bandarawela + Haputale". */
    public String areaLabel() {
        return areas == null || areas.isEmpty() ? "—"
                : areas.stream().map(RouteArea::getName).sorted()
                       .collect(java.util.stream.Collectors.joining(" + "));
    }

    @Column(name = "planned_date", nullable = false)
    private LocalDate plannedDate;

    /**
     * The month this round counts against, held as its first day.
     *
     * <p>Separate from the date it left, because a round planned for the end of one
     * month often goes out at the start of the next. Reporting wants the month it
     * belongs to, and deriving that from the date would put it in the wrong one.
     */
    @Column(name = "run_month")
    private LocalDate runMonth;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private DeliveryRunStatus status = DeliveryRunStatus.OPEN;

    @Column(length = 300)
    private String notes;

    /**
     * Who is entering bills into it. A run left open overnight would otherwise claim
     * the next morning's bills with nobody to ask about it.
     */
    @Column(name = "opened_by", length = 100)
    private String openedBy;

    @Column(name = "opened_at", nullable = false)
    @Builder.Default
    private LocalDateTime openedAt = LocalDateTime.now();

    @Column(name = "closed_by", length = 100)
    private String closedBy;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;
}
