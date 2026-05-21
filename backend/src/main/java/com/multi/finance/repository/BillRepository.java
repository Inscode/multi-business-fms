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

    long countByBusinessAndStatus(BusinessType business, BillStatus status);

    long countByBusinessAndStatusIn(BusinessType business, List<BillStatus> statuses);

    @Query("SELECT COUNT(b) FROM Bill b WHERE b.billSource = 'DRAFT'")
    long countAllDrafts();

    List<Bill> findAllByOrderByCreatedAtDesc();
    List<Bill> findByBusinessOrderByCreatedAtDesc(BusinessType business);
    List<Bill> findByStatusOrderByCreatedAtDesc(BillStatus status);
    List<Bill> findByBusinessAndStatusOrderByCreatedAtDesc(BusinessType business, BillStatus status);
    List<Bill> findByStatusInOrderByCreatedAtDesc(List<BillStatus> statuses);

}
