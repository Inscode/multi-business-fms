package com.multi.finance.repository;


import com.multi.finance.entity.Bill;
import com.multi.finance.enums.BillStatus;
import com.multi.finance.enums.BusinessType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface BillRepository extends JpaRepository<Bill, Long> {
    List<Bill> findByBusinessAndBillDate(String business, LocalDate billDate);

    List<Bill> findByBusinessAndStatus(String business, String status);

    List<Bill> findByBusinessAndBillDateAndStatusNot(
            BusinessType business, LocalDate billDate, BillStatus status);

    List<Bill> findByCurrentHolderIdAndStatus(Long workerId, String status);

    List<Bill> findByBusiness(BusinessType business);

    @Query("SELECT b FROM Bill b WHERE b.business = :business " + "AND b.billDate = :date " + "AND b.status NOT IN ('CONFIRMED', 'CANCELLED')")
    List<Bill> findUnconfirmedByBusinessAndDate(
            @Param("business") BusinessType business,
            @Param("date") LocalDate date);

    List<Bill> findByBusinessAndBillDateBetween(
            String business, LocalDate from , LocalDate to
    );
}
