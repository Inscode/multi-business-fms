package com.multi.finance.repository;

import com.multi.finance.entity.PaymentGroup;
import com.multi.finance.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentGroupRepository extends JpaRepository<PaymentGroup, Long> {
    List<PaymentGroup> findByStatus(PaymentStatus status);
    List<PaymentGroup> findByStatusOrderByCreatedAtDesc(PaymentStatus status);
    List<PaymentGroup> findAllByOrderByCreatedAtDesc();
}
