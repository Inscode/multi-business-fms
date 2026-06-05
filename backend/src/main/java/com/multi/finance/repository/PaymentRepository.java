package com.multi.finance.repository;
import com.multi.finance.entity.Payment;
import com.multi.finance.enums.BillStatus;
import com.multi.finance.enums.PaymentStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findByBillId(Long billId);
    boolean existsByBillIdAndStatus(Long billId, PaymentStatus status);
    List<Payment> findByGroupId(Long groupId);
    List<Payment> findByStatus(PaymentStatus status);

    // JOIN FETCH bill + enteredBy to avoid N+1 when mapping payment responses
    @Query("SELECT p FROM Payment p JOIN FETCH p.bill ORDER BY p.createdAt DESC")
    List<Payment> findAllWithBill();

    @Query("SELECT p FROM Payment p JOIN FETCH p.bill WHERE p.status = :status ORDER BY p.createdAt DESC")
    List<Payment> findByStatusWithBill(@Param("status") PaymentStatus status);

    // Load all payments for a list of group IDs in one query
    @Query("SELECT p FROM Payment p JOIN FETCH p.bill WHERE p.group.id IN :groupIds")
    List<Payment> findByGroupIdIn(@Param("groupIds") List<Long> groupIds);

    long countByStatus(PaymentStatus status);
    List<Payment> findByPaymentDate(LocalDate date);
    List<Payment> findByBillIdAndStatus(Long billId, String status);
    long countByStatusAndConfirmedAtBetween(
            PaymentStatus status, LocalDateTime start, LocalDateTime end
    );

    @Query("SELECT COALESCE(SUM(p.amount),0) FROM Payment p " + "WHERE p.status = 'CONFIRMED' AND p.confirmedAt BETWEEN :start AND :end")
    BigDecimal sumConfirmedAmountBetween(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    List<Payment> findByStatusOrderByCreatedAtDesc(PaymentStatus status);

    List<Payment> findByEnteredByIdAndStatusOrderByCreatedAtDesc(Long enteredById, PaymentStatus status);

    @Query("SELECT COUNT(p) FROM Payment p WHERE p.paymentDate = :date AND p.bill.business = :business")
    long countByPaymentDateAndBillBusiness(
            @Param("date") java.time.LocalDate date,
            @Param("business") com.multi.finance.enums.BusinessType business);

    @Query("SELECT COUNT(p) FROM Payment p WHERE p.status = :paymentStatus AND p.bill.status = :billStatus")
    long countByPaymentStatusAndBillStatus(
            @Param("paymentStatus") PaymentStatus paymentStatus,
            @Param("billStatus") BillStatus billStatus
    );
}