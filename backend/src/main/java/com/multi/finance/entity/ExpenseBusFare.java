package com.multi.finance.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity @Table(name = "expense_bus_fares")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ExpenseBusFare {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "from_location", nullable = false)
    private String fromLocation;
    @Column(name = "to_location", nullable = false)
    private String toLocation;
    @Column(nullable = false)
    private BigDecimal amount;
    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
