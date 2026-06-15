package com.multi.finance.repository;


import com.multi.finance.entity.Bill;
import com.multi.finance.enums.BillStatus;
import com.multi.finance.enums.BusinessType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface BillRepository extends JpaRepository<Bill, Long> {
    List<Bill> findByBusinessAndBillDate(BusinessType business, LocalDate billDate);

    List<Bill> findByBusinessAndBillDateAndStatusNot(
            BusinessType business, LocalDate billDate, BillStatus status);

    List<Bill> findByCurrentHolderIdAndStatus(Long workerId, BillStatus status);

    List<Bill> findByBusiness(BusinessType business);

    @Query("SELECT b FROM Bill b WHERE b.business = :business " + "AND b.billDate = :date " + "AND b.status NOT IN ('COMPLETED', 'CANCELLED')")
    List<Bill> findUnconfirmedByBusinessAndDate(
            @Param("business") BusinessType business,
            @Param("date") LocalDate date);

    List<Bill> findByBusinessAndBillDateBetween(
            String business, LocalDate from , LocalDate to
    );
    long countByBillDate(LocalDate date);

    long countByBusinessAndBillDateAndStatusNot(BusinessType business, LocalDate date, BillStatus status);
    List<Bill> findTop5ByOrderByCreatedAtDesc();

    long countByBillDateAndStatusNot(LocalDate date, BillStatus status);
    long countByStatus(BillStatus status);
    long countByBillDateAndStatus(LocalDate date, BillStatus status);
    List<Bill> findByStatus(BillStatus status);
    long countByStatusIn(List<BillStatus> statuses);


    List<Bill> findByBusinessAndStatus(BusinessType business, BillStatus status);

    @Query("SELECT COALESCE(SUM(b.balanceRemaining), 0) FROM Bill b " +
            "WHERE b.business = :business " +
            "AND b.status NOT IN ('CANCELLED', 'COMPLETED')")
    BigDecimal sumOutstandingByBusiness(@Param("business") BusinessType business);

    @Query("SELECT COALESCE(SUM(b.balanceRemaining), 0) FROM Bill b " +
            "WHERE b.business = :business " +
            "AND b.status NOT IN ('CANCELLED', 'COMPLETED') " +
            "AND (b.willBeLinked IS NULL OR b.willBeLinked = false)")
    BigDecimal sumOutstandingByBusinessExcludingLinking(@Param("business") BusinessType business);

    @Query("SELECT COALESCE(SUM(b.balanceRemaining), 0) FROM Bill b " +
            "WHERE b.business = :business AND b.billType = 'CASH' " +
            "AND b.balanceRemaining > 0 AND b.status NOT IN ('CANCELLED', 'COMPLETED')")
    BigDecimal sumCashPendingByBusiness(@Param("business") BusinessType business);

    @Query("SELECT COALESCE(SUM(b.balanceRemaining), 0) FROM Bill b " +
            "WHERE b.business = :business AND b.billType = 'CASH' " +
            "AND b.balanceRemaining > 0 AND b.status NOT IN ('CANCELLED', 'COMPLETED') " +
            "AND b.billDate <= :cutoff")
    BigDecimal sumCashSeriousByBusiness(@Param("business") BusinessType business,
                                        @Param("cutoff") LocalDate cutoff);

    long countByBusinessAndStatus(BusinessType business, BillStatus status);

    long countByBusinessAndStatusIn(BusinessType business, List<BillStatus> statuses);

    @Query("SELECT COUNT(b) FROM Bill b WHERE b.billSource = 'DRAFT'")
    long countAllDrafts();

    List<Bill> findAllByOrderByCreatedAtDesc();
    List<Bill> findByBusinessOrderByCreatedAtDesc(BusinessType business);
    List<Bill> findByStatusOrderByCreatedAtDesc(BillStatus status);
    List<Bill> findByBusinessAndStatusOrderByCreatedAtDesc(BusinessType business, BillStatus status);
    List<Bill> findByStatusInOrderByCreatedAtDesc(List<BillStatus> statuses);

    // Role-scoped: only return bills whose business is in the allowed set
    List<Bill> findByBusinessInOrderByCreatedAtDesc(List<BusinessType> businesses);
    List<Bill> findByBusinessInAndStatusOrderByCreatedAtDesc(List<BusinessType> businesses, BillStatus status);

    // Shop accountant: RETAIL_SHOP bills + any bill currently at the shop (status filter)
    @Query("SELECT b FROM Bill b WHERE b.business = 'RETAIL_SHOP' " +
           "OR b.status IN ('SHOP_RECEIVED', 'SHOP_WORKER_ASSIGNED') " +
           "ORDER BY b.createdAt DESC")
    List<Bill> findShopAccountantBills();

    @Query("SELECT b FROM Bill b WHERE (b.business = 'RETAIL_SHOP' " +
           "OR b.status IN ('SHOP_RECEIVED', 'SHOP_WORKER_ASSIGNED')) " +
           "AND b.status = :status " +
           "ORDER BY b.createdAt DESC")
    List<Bill> findShopAccountantBillsByStatus(@Param("status") BillStatus status);

    // SYSTEM bills not yet assigned to any SummaryLoadBill — excludes willBeLinked bills
    @Query("SELECT b FROM Bill b WHERE b.business = 'RAINCO' AND b.billSource = 'SYSTEM' " +
           "AND (b.willBeLinked = false OR b.willBeLinked IS NULL) " +
           "AND NOT EXISTS (SELECT sb FROM SummaryLoadBill slb JOIN slb.systemBills sb WHERE sb = b) " +
           "ORDER BY b.billDate DESC")
    List<Bill> findUnassignedSystemBills();

    // DRAFT/MANUAL bills not yet linked to any system bill via BillStockLink (all businesses)
    @Query("SELECT b FROM Bill b WHERE b.billSource IN ('DRAFT', 'MANUAL') " +
           "AND NOT EXISTS (SELECT l FROM BillStockLink l WHERE l.childBill = b) " +
           "ORDER BY b.billDate DESC")
    List<Bill> findUnlinkedDraftManualBills();

    // All DRAFT/MANUAL bills — for the unlinked dashboard
    @Query("SELECT b FROM Bill b WHERE b.billSource IN ('DRAFT', 'MANUAL') ORDER BY b.billDate DESC")
    List<Bill> findAllDraftManualBills();

    // SYSTEM bills available for linking: willBeLinked=true AND not yet used as parent
    @Query("SELECT b FROM Bill b WHERE b.billSource = 'SYSTEM' " +
           "AND b.willBeLinked = true " +
           "AND NOT EXISTS (SELECT l FROM BillStockLink l WHERE l.systemBill = b) " +
           "ORDER BY b.billDate DESC")
    List<Bill> findAvailableSystemBillsForLinking();

    // Exclude completed/cancelled — used when hideCompleted=true
    List<Bill> findByStatusNotInOrderByCreatedAtDesc(List<BillStatus> statuses);

    List<Bill> findByBusinessAndStatusNotInOrderByCreatedAtDesc(BusinessType business, List<BillStatus> statuses);

    List<Bill> findByBusinessInAndStatusNotInOrderByCreatedAtDesc(List<BusinessType> businesses, List<BillStatus> statuses);

    // ── Date-bounded queries (from + to both provided) ─────────────

    List<Bill> findByBillDateBetweenAndStatusNotInOrderByCreatedAtDesc(LocalDate from, LocalDate to, List<BillStatus> statuses);
    List<Bill> findByBillDateBetweenOrderByCreatedAtDesc(LocalDate from, LocalDate to);
    List<Bill> findByStatusAndBillDateBetweenOrderByCreatedAtDesc(BillStatus status, LocalDate from, LocalDate to);

    List<Bill> findByBusinessAndBillDateBetweenAndStatusNotInOrderByCreatedAtDesc(BusinessType business, LocalDate from, LocalDate to, List<BillStatus> statuses);
    List<Bill> findByBusinessAndBillDateBetweenOrderByCreatedAtDesc(BusinessType business, LocalDate from, LocalDate to);
    List<Bill> findByBusinessAndStatusAndBillDateBetweenOrderByCreatedAtDesc(BusinessType business, BillStatus status, LocalDate from, LocalDate to);

    List<Bill> findByBusinessInAndBillDateBetweenAndStatusNotInOrderByCreatedAtDesc(List<BusinessType> businesses, LocalDate from, LocalDate to, List<BillStatus> statuses);
    List<Bill> findByBusinessInAndBillDateBetweenOrderByCreatedAtDesc(List<BusinessType> businesses, LocalDate from, LocalDate to);
    List<Bill> findByBusinessInAndStatusAndBillDateBetweenOrderByCreatedAtDesc(List<BusinessType> businesses, BillStatus status, LocalDate from, LocalDate to);

    @Query("SELECT b FROM Bill b WHERE (b.business = 'RETAIL_SHOP' OR b.status IN ('SHOP_RECEIVED', 'SHOP_WORKER_ASSIGNED')) AND b.status NOT IN ('COMPLETED', 'CANCELLED') AND b.billDate BETWEEN :from AND :to ORDER BY b.createdAt DESC")
    List<Bill> findShopAccountantActiveBillsInDateRange(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("SELECT b FROM Bill b WHERE (b.business = 'RETAIL_SHOP' OR b.status IN ('SHOP_RECEIVED', 'SHOP_WORKER_ASSIGNED')) AND b.billDate BETWEEN :from AND :to ORDER BY b.createdAt DESC")
    List<Bill> findShopAccountantBillsInDateRange(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("SELECT b FROM Bill b WHERE (b.business = 'RETAIL_SHOP' OR b.status IN ('SHOP_RECEIVED', 'SHOP_WORKER_ASSIGNED')) AND b.status = :status AND b.billDate BETWEEN :from AND :to ORDER BY b.createdAt DESC")
    List<Bill> findShopAccountantBillsByStatusInDateRange(@Param("status") BillStatus status, @Param("from") LocalDate from, @Param("to") LocalDate to);

    // ── Overdue queries (before cutoff, pending only) ───────────────

    List<Bill> findByBillDateBeforeAndStatusNotInOrderByBillDateAsc(LocalDate cutoff, List<BillStatus> statuses);
    List<Bill> findByBusinessAndBillDateBeforeAndStatusNotInOrderByBillDateAsc(BusinessType business, LocalDate cutoff, List<BillStatus> statuses);
    List<Bill> findByBusinessInAndBillDateBeforeAndStatusNotInOrderByBillDateAsc(List<BusinessType> businesses, LocalDate cutoff, List<BillStatus> statuses);

    @Query("SELECT b FROM Bill b WHERE (b.business = 'RETAIL_SHOP' OR b.status IN ('SHOP_RECEIVED', 'SHOP_WORKER_ASSIGNED')) AND b.status NOT IN ('COMPLETED', 'CANCELLED') AND b.billDate < :cutoff ORDER BY b.billDate ASC")
    List<Bill> findShopAccountantOverdueBills(@Param("cutoff") LocalDate cutoff);

    // ── Overdue count (for badge) ───────────────────────────────────
    long countByBillDateBeforeAndStatusNotIn(LocalDate cutoff, List<BillStatus> statuses);
    long countByBusinessInAndBillDateBeforeAndStatusNotIn(List<BusinessType> businesses, LocalDate cutoff, List<BillStatus> statuses);

    @Query("SELECT COUNT(b) FROM Bill b WHERE (b.business = 'RETAIL_SHOP' OR b.status IN ('SHOP_RECEIVED', 'SHOP_WORKER_ASSIGNED')) AND b.status NOT IN ('COMPLETED', 'CANCELLED') AND b.billDate < :cutoff")
    long countShopAccountantOverdueBills(@Param("cutoff") LocalDate cutoff);

    @Query("SELECT b FROM Bill b WHERE (b.business = 'RETAIL_SHOP' " +
           "OR b.status IN ('SHOP_RECEIVED', 'SHOP_WORKER_ASSIGNED')) " +
           "AND b.status NOT IN ('COMPLETED', 'CANCELLED') " +
           "ORDER BY b.createdAt DESC")
    List<Bill> findShopAccountantActiveBills();

    // All SYSTEM bills flagged as willBeLinked=true (linking bills tab)
    List<Bill> findByWillBeLinkedTrueOrderByBillDateDesc();

    // SYSTEM bills that ARE linked (have child bills)
    @Query("SELECT DISTINCT l.systemBill FROM BillStockLink l")
    List<Bill> findLinkedSystemBills();

    // DRAFT/MANUAL bills that ARE child bills in a link
    @Query("SELECT DISTINCT l.childBill FROM BillStockLink l WHERE l.childBill.billSource IN ('DRAFT', 'MANUAL')")
    List<Bill> findLinkedChildBills();

}
