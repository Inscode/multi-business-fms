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
            "AND b.settledOn IS NULL " +
            "AND b.status NOT IN ('CANCELLED', 'COMPLETED', 'AWAITING_CONFIRMATION')")
    BigDecimal sumOutstandingByBusiness(@Param("business") BusinessType business);

    @Query("SELECT COALESCE(SUM(b.balanceRemaining), 0) FROM Bill b " +
            "WHERE b.business = :business " +
            "AND b.status NOT IN ('CANCELLED', 'COMPLETED', 'AWAITING_CONFIRMATION') " +
            "AND b.settledOn IS NULL " +
            "AND (b.willBeLinked IS NULL OR b.willBeLinked = false)")
    BigDecimal sumOutstandingByBusinessExcludingLinking(@Param("business") BusinessType business);

    @Query("SELECT COALESCE(SUM(b.balanceRemaining), 0) FROM Bill b " +
            "WHERE b.business = :business AND b.billType = 'CASH' " +
            "AND b.balanceRemaining > 0 AND b.settledOn IS NULL " +
            "AND b.status NOT IN ('CANCELLED', 'COMPLETED', 'AWAITING_CONFIRMATION')")
    BigDecimal sumCashPendingByBusiness(@Param("business") BusinessType business);

    @Query("SELECT COALESCE(SUM(b.balanceRemaining), 0) FROM Bill b " +
            "WHERE b.business = :business AND b.billType = 'CASH' " +
            "AND b.balanceRemaining > 0 AND b.settledOn IS NULL " +
            "AND b.status NOT IN ('CANCELLED', 'COMPLETED', 'AWAITING_CONFIRMATION') " +
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

    /**
     * Whether any bill already holds this number, cancelled or not.
     *
     * <p>A cancelled bill keeps its number: it is still a record of something that
     * happened, and reusing the number would put two bills under one identity. A
     * number is only released by deleting the bill outright.
     */
    @Query("SELECT COUNT(b) > 0 FROM Bill b WHERE b.billNumber = :billNumber "
         + "AND b.business = :business")
    boolean existsActiveByBillNumberAndBusiness(@Param("billNumber") String billNumber,
                                                @Param("business") BusinessType business);

    /** The bill on this number, whatever its status — a number is held until deleted. */
    @Query("SELECT b FROM Bill b WHERE b.billNumber = :billNumber AND b.business = :business")
    java.util.Optional<Bill> findActiveByBillNumberAndBusiness(
            @Param("billNumber") String billNumber, @Param("business") BusinessType business);

    /** Lets invoicing attach to a bill already entered by hand instead of raising a second one. */
    java.util.Optional<Bill> findByBillNumberAndBusiness(String billNumber, BusinessType business);

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
    // Settled and cancelled bills are left out of every month-end view: they are
    // closed, and listing them only buries the ones still needing work.
    @Query("SELECT b FROM Bill b WHERE b.billSource IN ('DRAFT', 'MANUAL') " +
           "AND b.status NOT IN ('COMPLETED', 'CANCELLED') " +
           "AND NOT EXISTS (SELECT l FROM BillStockLink l WHERE l.childBill = b) " +
           "ORDER BY b.billDate DESC")
    List<Bill> findUnlinkedDraftManualBills();

    // All DRAFT/MANUAL bills — for the unlinked dashboard
    @Query("SELECT b FROM Bill b WHERE b.billSource IN ('DRAFT', 'MANUAL') " +
           "AND b.status NOT IN ('COMPLETED', 'CANCELLED') ORDER BY b.billDate DESC")
    List<Bill> findAllDraftManualBills();

    // SYSTEM bills available for linking: willBeLinked=true AND not yet used as parent
    @Query("SELECT b FROM Bill b WHERE b.billSource = 'SYSTEM' " +
           "AND b.willBeLinked = true " +
           "AND b.status NOT IN ('COMPLETED', 'CANCELLED') " +
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
    /** Month-end linking list. Closed bills are excluded — nothing left to link. */
    @Query("SELECT b FROM Bill b WHERE b.willBeLinked = true " +
           "AND b.status NOT IN ('COMPLETED', 'CANCELLED') ORDER BY b.billDate DESC")
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

    // ── Every number used, not just the highest ──────────────────────────────
    // The suggestion list needs the gaps, and a MAX() can't show a hole in the middle.

    // Cancelled bills count as used: the number stays with them, so it is neither
    // offered again nor reported as a gap somebody forgot to enter.
    @Query(value = "SELECT CAST(SUBSTRING(bill_number FROM 5) AS INTEGER) FROM bills " +
                   "WHERE business = :business AND bill_number ~ '^MAN-[0-9]+$'",
           nativeQuery = true)
    List<Integer> findUsedManualBillNumbers(@Param("business") String business);

    @Query(value = "SELECT CAST(SUBSTRING(bill_number FROM 5) AS INTEGER) FROM bills " +
                   "WHERE business = :business AND bill_number ~ '^SYS-[0-9]+$'",
           nativeQuery = true)
    List<Integer> findUsedSystemBillNumbers(@Param("business") String business);

    @Query(value = "SELECT CAST(SUBSTRING(bill_number FROM 4) AS INTEGER) FROM bills " +
                   "WHERE bill_number ~ '^BK-[0-9]+$'", nativeQuery = true)
    List<Integer> findUsedSharedBookBillNumbers();

    // Global full-table search across all statuses/dates — excludes DEMO business
    @Query("SELECT b FROM Bill b WHERE b.business <> 'DEMO' AND (LOWER(b.billNumber) LIKE LOWER(CONCAT('%',:q,'%')) OR LOWER(b.customerName) LIKE LOWER(CONCAT('%',:q,'%'))) ORDER BY b.billDate DESC")
    List<Bill> globalSearch(@Param("q") String q, org.springframework.data.domain.Pageable pageable);

    // ── Copilot queries ───────────────────────────────────────────────────────

    // settledOn IS NULL: the balance is collected on another bill, so listing it here
    // would have the copilot chase the same money twice.
    @Query("SELECT b FROM Bill b LEFT JOIN FETCH b.customer WHERE b.fullyPaid = false AND b.settledOn IS NULL AND b.status NOT IN :excluded ORDER BY b.billDate ASC")
    List<Bill> findOutstandingBillsWithCustomer(@Param("excluded") List<BillStatus> excluded);

    @Query("SELECT b FROM Bill b LEFT JOIN FETCH b.customer WHERE LOWER(b.customerName) LIKE LOWER(CONCAT('%', :name, '%')) OR (b.customer IS NOT NULL AND LOWER(b.customer.name) LIKE LOWER(CONCAT('%', :name, '%'))) ORDER BY b.createdAt DESC")
    List<Bill> findByCustomerNameContainingIgnoreCase(@Param("name") String name);

    /**
     * Every still-owing bill as of a cut-off date — the working set for a month-end
     * reconciliation sweep. Status is deliberately ignored (except CANCELLED): a bill
     * force-completed with money still owed must still appear here.
     */
    @Query("SELECT b FROM Bill b WHERE b.balanceRemaining > 0 " +
           "AND b.status <> 'CANCELLED' AND b.billDate <= :cutoff " +
           "AND (b.willBeLinked IS NULL OR b.willBeLinked = false) " +
           "AND (:business IS NULL OR b.business = :business) " +
           "AND (:area IS NULL OR b.area = :area) " +
           "ORDER BY b.area ASC, b.billDate ASC")
    List<Bill> findPendingForAudit(@Param("cutoff") LocalDate cutoff,
                                   @Param("business") BusinessType business,
                                   @Param("area") String area);

    /** Bills the admin has hidden from the aging report, so they can be reviewed. */
    @org.springframework.data.jpa.repository.Query(
        "SELECT b FROM Bill b WHERE b.business = :business AND b.excludedFromAging = true "
      + "ORDER BY b.billDate DESC")
    List<Bill> findExcludedFromAging(
        @org.springframework.data.repository.query.Param("business")
        com.multi.finance.enums.BusinessType business);

    /** Everything carried on one lorry round. */
    List<Bill> findByDeliveryRunIdOrderByBillNumberAsc(Long deliveryRunId);

    default List<Bill> findByDeliveryRunId(Long deliveryRunId) {
        return findByDeliveryRunIdOrderByBillNumberAsc(deliveryRunId);
    }

    /**
     * Sales and what is still owed, for one month, split by business.
     *
     * <p>The month a bill counts against is its round's month where it went out on one,
     * and its own date otherwise. A round planned for the end of August that leaves in
     * September still belongs to August, and that is the figure the month is judged on.
     *
     * <p>Cancelled bills are excluded: they are void, not sales.
     */
    @Query(value =
        "SELECT b.business, "
      + "       COUNT(*), "
      + "       COALESCE(SUM(b.total_amount), 0), "
      + "       COALESCE(SUM(b.amount_paid), 0), "
      + "       COALESCE(SUM(b.balance_remaining), 0) "
      + "  FROM bills b "
      + "  LEFT JOIN delivery_runs r ON r.id = b.delivery_run_id "
      + " WHERE date_trunc('month', COALESCE(r.run_month, b.bill_date)) = "
      + "       date_trunc('month', CAST(:month AS date)) "
      + "   AND b.status <> 'CANCELLED' "
      + "   AND b.settled_on_bill_id IS NULL "
      + "   AND (CAST(:mode AS text) IS NULL OR b.delivery_mode = CAST(:mode AS text)) "
      + " GROUP BY b.business ORDER BY b.business",
        nativeQuery = true)
    List<Object[]> monthSalesByBusiness(@Param("month") java.time.LocalDate month,
                                        @Param("mode") String mode);

    /**
     * Bills that could still join a round: on no run, not cancelled, and dated near it.
     *
     * <p>Dated near it rather than anywhere, because a bill from three months ago
     * joining today's lorry is almost always a mis-click rather than an intention.
     */
    @Query("SELECT b FROM Bill b WHERE b.deliveryRun IS NULL "
         + "AND b.status <> com.multi.finance.enums.BillStatus.CANCELLED "
         + "AND b.billDate BETWEEN :from AND :to "
         + "ORDER BY b.billDate DESC, b.billNumber ASC")
    List<Bill> findRunCandidates(@Param("from") java.time.LocalDate from,
                                 @Param("to") java.time.LocalDate to);

    /** The bills whose money is collected on this one. */
    List<Bill> findBySettledOnId(Long settledOnId);

    /**
     * Hand-written bills a bill could be collected on.
     *
     * <p>Not narrowed by customer: the shop's bill is written by hand and the system copy
     * is typed later, so the two rarely carry the same spelling of the name — matching on
     * it would hide the very bill being looked for. The business and the source are what
     * hold, so those are what filter, and the admin picks by number from the list.
     */
    @Query("SELECT b FROM Bill b WHERE b.id <> :billId "
         + "AND b.settledOn IS NULL "
         + "AND b.status <> com.multi.finance.enums.BillStatus.CANCELLED "
         + "AND b.business = :business "
         + "AND b.billSource IN :sources "
         + "ORDER BY b.billDate DESC, b.id DESC")
    List<Bill> findSettleCandidates(@Param("billId") Long billId,
                                    @Param("business") BusinessType business,
                                    @Param("sources") List<com.multi.finance.enums.BillSource> sources);

    /**
     * Every bill belonging to a customer, by id or by the name typed on it.
     *
     * <p>Both, because bills predating customer records and bills brought in by import
     * carry only the name. Matching on the id alone would rate a customer of ten years
     * on whichever of their bills happened to get linked.
     */
    @Query("SELECT b FROM Bill b LEFT JOIN FETCH b.customer "
         + "WHERE (b.customer IS NOT NULL AND b.customer.id = :customerId) "
         + "   OR (b.customer IS NULL AND UPPER(TRIM(b.customerName)) = UPPER(TRIM(:name))) "
         + "ORDER BY b.billDate DESC")
    List<Bill> findAllForCustomer(@Param("customerId") Long customerId,
                                  @Param("name") String name);

    List<Bill> findByBusinessOrderByBillDateDesc(BusinessType business);
}
