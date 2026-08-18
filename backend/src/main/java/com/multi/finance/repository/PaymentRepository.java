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
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findByBillId(Long billId);

    /** The payment auto-created from an admin cash collection, if any. */
    Optional<Payment> findByCollectionNoteId(Long collectionNoteId);
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

    @Query("SELECT p FROM Payment p JOIN FETCH p.bill WHERE p.paymentDate BETWEEN :from AND :to ORDER BY p.createdAt DESC")
    List<Payment> findAllWithBillBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("SELECT p FROM Payment p JOIN FETCH p.bill WHERE p.status = :status AND p.paymentDate BETWEEN :from AND :to ORDER BY p.createdAt DESC")
    List<Payment> findByStatusWithBillBetween(@Param("status") PaymentStatus status, @Param("from") LocalDate from, @Param("to") LocalDate to);

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

    /** Sum of ENTERED (not-yet-confirmed) payments for a bill — used to prevent double-entry overpayment */
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.bill.id = :billId AND p.status = 'ENTERED'")
    BigDecimal sumEnteredForBill(@Param("billId") Long billId);

    // ── Copilot queries ───────────────────────────────────────────────────────

    @Query("SELECT p FROM Payment p JOIN FETCH p.bill WHERE p.bill.id IN :billIds ORDER BY p.paymentDate DESC")
    List<Payment> findByBillIdIn(@Param("billIds") List<Long> billIds);

    /** Returns [billId, maxPaymentDate] pairs — single bulk query, avoids N+1 in aging report */
    @Query("SELECT p.bill.id, MAX(p.paymentDate) FROM Payment p WHERE p.bill.id IN :billIds AND p.status = 'CONFIRMED' GROUP BY p.bill.id")
    List<Object[]> findLastConfirmedDatesByBillIds(@Param("billIds") List<Long> billIds);

    // ── Collection Health dashboard ──────────────────────────────────

    /** [workerId, workerName, totalCollected, paymentCount, avgDaysToCollect, partialCount] per collector in range */
    @Query(value = "SELECT w.id, w.full_name, COALESCE(SUM(p.amount),0), COUNT(p.id), " +
           "COALESCE(AVG(p.payment_date - b.bill_date),0), " +
           "COALESCE(SUM(CASE WHEN p.is_partial THEN 1 ELSE 0 END),0) " +
           "FROM payments p JOIN workers w ON w.id = p.collected_by_worker_id " +
           "JOIN bills b ON b.id = p.bill_id " +
           "WHERE p.status = 'CONFIRMED' AND b.business = :business " +
           "AND p.payment_date BETWEEN :from AND :to " +
           "GROUP BY w.id, w.full_name ORDER BY 3 DESC", nativeQuery = true)
    List<Object[]> findCollectorPerformance(@Param("business") String business,
                                             @Param("from") LocalDate from,
                                             @Param("to") LocalDate to);

    /** [chequeDate, totalAmount, count] grouped by cheque date, for cheques expected in the given range */
    @Query(value = "SELECT p.cheque_date, COALESCE(SUM(p.amount),0), COUNT(p.id) " +
           "FROM payments p JOIN bills b ON b.id = p.bill_id " +
           "WHERE p.payment_type = 'CHEQUE' AND b.business = :business " +
           "AND p.cheque_date BETWEEN :from AND :to AND p.status <> 'REJECTED' AND p.status <> 'RETURNED' " +
           "GROUP BY p.cheque_date ORDER BY p.cheque_date ASC", nativeQuery = true)
    List<Object[]> findUpcomingChequeTotals(@Param("business") String business,
                                             @Param("from") LocalDate from,
                                             @Param("to") LocalDate to);

    /** [chequeDate, amount, customerName, billNumber] — cheques past their date, still unconfirmed */
    @Query(value = "SELECT p.cheque_date, p.amount, b.customer_name, b.bill_number " +
           "FROM payments p JOIN bills b ON b.id = p.bill_id " +
           "WHERE p.payment_type = 'CHEQUE' AND b.business = :business " +
           "AND p.cheque_date < :today AND p.status = 'ENTERED' ORDER BY p.cheque_date ASC", nativeQuery = true)
    List<Object[]> findStaleCheques(@Param("business") String business, @Param("today") LocalDate today);

    /** [customerName, area, partialCount, returnedCount] for customers with at least one partial or returned payment */
    @Query(value = "SELECT b.customer_name, b.area, " +
           "SUM(CASE WHEN p.is_partial THEN 1 ELSE 0 END), SUM(CASE WHEN p.status='RETURNED' THEN 1 ELSE 0 END) " +
           "FROM payments p JOIN bills b ON b.id = p.bill_id " +
           "WHERE b.business = :business GROUP BY b.customer_name, b.area " +
           "HAVING SUM(CASE WHEN p.is_partial THEN 1 ELSE 0 END) > 0 OR SUM(CASE WHEN p.status='RETURNED' THEN 1 ELSE 0 END) > 0",
           nativeQuery = true)
    List<Object[]> findCustomerRiskRaw(@Param("business") String business);

    /** [paymentDate, cash, cheque, bankTransfer] — confirmed collections per day, split by method */
    @Query(value = "SELECT p.payment_date, " +
           "COALESCE(SUM(CASE WHEN p.payment_type='CASH' THEN p.amount ELSE 0 END),0), " +
           "COALESCE(SUM(CASE WHEN p.payment_type='CHEQUE' THEN p.amount ELSE 0 END),0), " +
           "COALESCE(SUM(CASE WHEN p.payment_type='BANK_TRANSFER' THEN p.amount ELSE 0 END),0) " +
           "FROM payments p JOIN bills b ON b.id = p.bill_id " +
           "WHERE p.status = 'CONFIRMED' AND b.business = :business " +
           "AND p.payment_date BETWEEN :from AND :to " +
           "GROUP BY p.payment_date ORDER BY p.payment_date ASC", nativeQuery = true)
    List<Object[]> findPaymentMixByDate(@Param("business") String business,
                                         @Param("from") LocalDate from,
                                         @Param("to") LocalDate to);

    /** [customerName, billNumber, bankName, chequeNumber, amount, status] — individual cheques for a given date */
    @Query(value = "SELECT b.customer_name, b.bill_number, p.bank_name, p.cheque_number, p.amount, p.status " +
           "FROM payments p JOIN bills b ON b.id = p.bill_id " +
           "WHERE p.payment_type = 'CHEQUE' AND b.business = :business " +
           "AND p.cheque_date = :date AND p.status <> 'REJECTED' AND p.status <> 'RETURNED' " +
           "ORDER BY p.amount DESC", nativeQuery = true)
    List<Object[]> findChequeDetailsByDate(@Param("business") String business,
                                           @Param("date") LocalDate date);

    /** Future-dated cheques (chequeDate >= today), optionally filtered by customer name substring */
    @Query("SELECT p FROM Payment p JOIN FETCH p.bill b " +
           "WHERE p.paymentType = com.multi.finance.enums.PaymentType.CHEQUE " +
           "AND p.chequeDate >= :today " +
           "AND p.status NOT IN (com.multi.finance.enums.PaymentStatus.REJECTED, com.multi.finance.enums.PaymentStatus.RETURNED) " +
           "AND (:customer IS NULL OR LOWER(b.customerName) LIKE LOWER(CONCAT('%', CAST(:customer AS string), '%'))) " +
           "ORDER BY p.chequeDate ASC")
    List<Payment> findFutureCheques(@Param("today") LocalDate today, @Param("customer") String customer);

    /** All cheque payments whose cheque number contains the given substring (no date constraint) */
    @Query("SELECT p FROM Payment p JOIN FETCH p.bill " +
           "WHERE p.paymentType = com.multi.finance.enums.PaymentType.CHEQUE " +
           "AND p.chequeNumber IS NOT NULL " +
           "AND LOWER(p.chequeNumber) LIKE LOWER(CONCAT('%', CAST(:chequeNumber AS string), '%')) " +
           "ORDER BY p.chequeDate ASC NULLS LAST")
    List<Payment> findByChequeNumberContaining(@Param("chequeNumber") String chequeNumber);

    /**
     * The most recent payment on a bill that carries its own photograph, entered by a
     * given person since a cut-off — the candidate for a second instrument handed over
     * in the same visit.
     *
     * <p>Its own photograph, not an inherited one: chaining would let a photo taken at
     * nine in the morning cover a payment at four in the afternoon, one two-hour hop at
     * a time.
     */
    @Query("SELECT p FROM Payment p "
         + "WHERE p.bill.id = :billId "
         + "AND p.receiptImageUrl IS NOT NULL "
         + "AND p.receiptSharedFrom IS NULL "
         + "AND p.enteredBy.id = :userId "
         + "AND p.createdAt >= :since "
         + "AND p.status <> com.multi.finance.enums.PaymentStatus.REJECTED "
         + "ORDER BY p.createdAt DESC")
    List<Payment> findRecentWithReceiptForBill(@Param("billId") Long billId,
                                               @Param("userId") Long userId,
                                               @Param("since") java.time.LocalDateTime since);
}