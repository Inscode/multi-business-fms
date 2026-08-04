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

    @Query("SELECT b FROM Bill b WHERE b.business = :business " + "AND b.billDate = :date " + "AND b.status NOT IN ('COMPLETED', 'AWAITING_CONFIRMATION', 'CANCELLED')")
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
    long countByStatusNot(BillStatus status);
    long countByBillDateAndStatus(LocalDate date, BillStatus status);
    List<Bill> findByStatus(BillStatus status);
    long countByStatusIn(List<BillStatus> statuses);


    List<Bill> findByBusinessAndStatus(BusinessType business, BillStatus status);

    @Query("SELECT COALESCE(SUM(b.balanceRemaining), 0) FROM Bill b " +
            "WHERE b.business = :business " +
            "AND b.status NOT IN ('CANCELLED', 'COMPLETED', 'AWAITING_CONFIRMATION')")
    BigDecimal sumOutstandingByBusiness(@Param("business") BusinessType business);

    @Query("SELECT COALESCE(SUM(b.balanceRemaining), 0) FROM Bill b " +
            "WHERE b.business = :business " +
            "AND b.status NOT IN ('CANCELLED', 'COMPLETED', 'AWAITING_CONFIRMATION') " +
            "AND (b.willBeLinked IS NULL OR b.willBeLinked = false)")
    BigDecimal sumOutstandingByBusinessExcludingLinking(@Param("business") BusinessType business);

    @Query("SELECT COALESCE(SUM(b.balanceRemaining), 0) FROM Bill b " +
            "WHERE b.business = :business AND b.billType = 'CASH' " +
            "AND b.balanceRemaining > 0 AND b.status NOT IN ('CANCELLED', 'COMPLETED', 'AWAITING_CONFIRMATION')")
    BigDecimal sumCashPendingByBusiness(@Param("business") BusinessType business);

    @Query("SELECT COALESCE(SUM(b.balanceRemaining), 0) FROM Bill b " +
            "WHERE b.business = :business AND b.billType = 'CASH' " +
            "AND b.balanceRemaining > 0 AND b.status NOT IN ('CANCELLED', 'COMPLETED', 'AWAITING_CONFIRMATION') " +
            "AND b.billDate <= :cutoff")
    BigDecimal sumCashSeriousByBusiness(@Param("business") BusinessType business,
                                        @Param("cutoff") LocalDate cutoff);

    long countByBusinessAndStatus(BusinessType business, BillStatus status);

    long countByBusinessAndStatusIn(BusinessType business, List<BillStatus> statuses);

    @Query("SELECT COUNT(b) FROM Bill b WHERE b.billSource = 'DRAFT'")
    long countAllDrafts();

    @Query("SELECT MAX(CAST(SUBSTRING(b.billNumber, 5) AS int)) FROM Bill b WHERE b.billNumber LIKE 'DFT-%' AND b.business = :business")
    Integer findMaxDraftSequenceByBusiness(@Param("business") BusinessType business);

    boolean existsByBillNumberAndBusiness(String billNumber, BusinessType business);

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

    @Query("SELECT b FROM Bill b WHERE (b.business = 'RETAIL_SHOP' OR b.status IN ('SHOP_RECEIVED', 'SHOP_WORKER_ASSIGNED')) AND b.status NOT IN ('COMPLETED', 'AWAITING_CONFIRMATION', 'CANCELLED') AND b.billDate BETWEEN :from AND :to ORDER BY b.createdAt DESC")
    List<Bill> findShopAccountantActiveBillsInDateRange(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("SELECT b FROM Bill b WHERE (b.business = 'RETAIL_SHOP' OR b.status IN ('SHOP_RECEIVED', 'SHOP_WORKER_ASSIGNED')) AND b.billDate BETWEEN :from AND :to ORDER BY b.createdAt DESC")
    List<Bill> findShopAccountantBillsInDateRange(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("SELECT b FROM Bill b WHERE (b.business = 'RETAIL_SHOP' OR b.status IN ('SHOP_RECEIVED', 'SHOP_WORKER_ASSIGNED')) AND b.status = :status AND b.billDate BETWEEN :from AND :to ORDER BY b.createdAt DESC")
    List<Bill> findShopAccountantBillsByStatusInDateRange(@Param("status") BillStatus status, @Param("from") LocalDate from, @Param("to") LocalDate to);

    // ── Overdue queries (before cutoff, pending only) ───────────────

    List<Bill> findByBillDateBeforeAndStatusNotInOrderByBillDateAsc(LocalDate cutoff, List<BillStatus> statuses);
    List<Bill> findByBusinessAndBillDateBeforeAndStatusNotInOrderByBillDateAsc(BusinessType business, LocalDate cutoff, List<BillStatus> statuses);
    List<Bill> findByBusinessInAndBillDateBeforeAndStatusNotInOrderByBillDateAsc(List<BusinessType> businesses, LocalDate cutoff, List<BillStatus> statuses);

    @Query("SELECT b FROM Bill b WHERE (b.business = 'RETAIL_SHOP' OR b.status IN ('SHOP_RECEIVED', 'SHOP_WORKER_ASSIGNED')) AND b.status NOT IN ('COMPLETED', 'AWAITING_CONFIRMATION', 'CANCELLED') AND b.billDate < :cutoff ORDER BY b.billDate ASC")
    List<Bill> findShopAccountantOverdueBills(@Param("cutoff") LocalDate cutoff);

    // ── Overdue count (for badge) ───────────────────────────────────
    long countByBillDateBeforeAndStatusNotIn(LocalDate cutoff, List<BillStatus> statuses);
    long countByBusinessInAndBillDateBeforeAndStatusNotIn(List<BusinessType> businesses, LocalDate cutoff, List<BillStatus> statuses);

    @Query("SELECT COUNT(b) FROM Bill b WHERE (b.business = 'RETAIL_SHOP' OR b.status IN ('SHOP_RECEIVED', 'SHOP_WORKER_ASSIGNED')) AND b.status NOT IN ('COMPLETED', 'AWAITING_CONFIRMATION', 'CANCELLED') AND b.billDate < :cutoff")
    long countShopAccountantOverdueBills(@Param("cutoff") LocalDate cutoff);

    @Query("SELECT b FROM Bill b WHERE (b.business = 'RETAIL_SHOP' " +
           "OR b.status IN ('SHOP_RECEIVED', 'SHOP_WORKER_ASSIGNED')) " +
           "AND b.status NOT IN ('COMPLETED', 'AWAITING_CONFIRMATION', 'CANCELLED') " +
           "ORDER BY b.createdAt DESC")
    List<Bill> findShopAccountantActiveBills();

    // All SYSTEM bills flagged as willBeLinked=true (linking bills tab)
    List<Bill> findByWillBeLinkedTrueOrderByBillDateDesc();

    // Settled CREDIT bills since cutoff — feeds the DSO-proxy trend on the Collection Health dashboard
    @Query("SELECT b FROM Bill b WHERE b.business = :business AND b.billType = 'CREDIT' " +
           "AND b.balanceRemaining = 0 AND b.billDate >= :cutoff AND b.status <> 'CANCELLED'")
    List<Bill> findSettledCreditBillsSince(@Param("business") BusinessType business, @Param("cutoff") LocalDate cutoff);

    // Current outstanding grouped by customer name — used to enrich the risky-customers list
    @Query("SELECT b.customerName, COALESCE(SUM(b.balanceRemaining),0) FROM Bill b " +
           "WHERE b.business = :business AND b.status NOT IN ('CANCELLED','COMPLETED','AWAITING_CONFIRMATION') GROUP BY b.customerName")
    List<Object[]> sumOutstandingGroupedByCustomerName(@Param("business") BusinessType business);

    // SYSTEM bills that ARE linked (have child bills)
    @Query("SELECT DISTINCT l.systemBill FROM BillStockLink l")
    List<Bill> findLinkedSystemBills();

    // DRAFT/MANUAL bills that ARE child bills in a link
    @Query("SELECT DISTINCT l.childBill FROM BillStockLink l WHERE l.childBill.billSource IN ('DRAFT', 'MANUAL')")
    List<Bill> findLinkedChildBills();

    // Active RAINCO bills eligible for backorder submission (any uncompleted, non-cancelled, non-linking bill)
    @Query("SELECT b FROM Bill b WHERE b.business = 'RAINCO' AND b.status NOT IN ('COMPLETED', 'AWAITING_CONFIRMATION', 'CANCELLED') AND (b.willBeLinked IS NULL OR b.willBeLinked = false) ORDER BY b.billDate DESC")
    List<Bill> findActiveRaincoBillsForBackorder();

    // RAINCO own MANUAL bills (MAN- prefix, separate sequence)
    @Query(value = "SELECT COALESCE(MAX(CAST(SUBSTRING(bill_number FROM 5) AS INTEGER)), 0) FROM bills " +
                   "WHERE business = :business AND bill_source = 'MANUAL' " +
                   "AND bill_number ~ '^MAN-[0-9]+$'", nativeQuery = true)
    Integer findMaxManualBillNumber(@Param("business") String business);

    @Query(value = "SELECT COALESCE(MAX(CAST(SUBSTRING(bill_number FROM 5) AS INTEGER)), 0) FROM bills " +
                   "WHERE business = :business AND bill_source = 'SYSTEM' " +
                   "AND bill_number ~ '^SYS-[0-9]+$'", nativeQuery = true)
    Integer findMaxSystemBillNumber(@Param("business") String business);

    // Shared physical book: PLASTIC MANUAL + STATIONERY MANUAL + RAINCO MANUAL_BOOK (all BK- prefix)
    // BK- is 3 chars so strip from position 4
    @Query(value = "SELECT COALESCE(MAX(CAST(SUBSTRING(bill_number FROM 4) AS INTEGER)), 0) FROM bills " +
                   "WHERE bill_number ~ '^BK-[0-9]+$'", nativeQuery = true)
    Integer findMaxSharedBookBillNumber();

    // Global full-table search across all statuses/dates — excludes DEMO business
    @Query("SELECT b FROM Bill b WHERE b.business <> 'DEMO' AND (LOWER(b.billNumber) LIKE LOWER(CONCAT('%',:q,'%')) OR LOWER(b.customerName) LIKE LOWER(CONCAT('%',:q,'%'))) ORDER BY b.billDate DESC")
    List<Bill> globalSearch(@Param("q") String q, org.springframework.data.domain.Pageable pageable);

    // ── Copilot queries ───────────────────────────────────────────────────────

    @Query("SELECT b FROM Bill b LEFT JOIN FETCH b.customer WHERE b.fullyPaid = false AND b.status NOT IN :excluded ORDER BY b.billDate ASC")
    List<Bill> findOutstandingBillsWithCustomer(@Param("excluded") List<BillStatus> excluded);

    @Query("SELECT b FROM Bill b LEFT JOIN FETCH b.customer WHERE LOWER(b.customerName) LIKE LOWER(CONCAT('%', :name, '%')) OR (b.customer IS NOT NULL AND LOWER(b.customer.name) LIKE LOWER(CONCAT('%', :name, '%'))) ORDER BY b.createdAt DESC")
    List<Bill> findByCustomerNameContainingIgnoreCase(@Param("name") String name);

}
