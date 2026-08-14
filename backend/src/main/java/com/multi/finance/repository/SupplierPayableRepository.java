package com.multi.finance.repository;

import com.multi.finance.entity.SupplierPayable;
import com.multi.finance.enums.BusinessType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface SupplierPayableRepository extends JpaRepository<SupplierPayable, Long> {

    List<SupplierPayable> findAllByOrderByDueDateAsc();

    /** Unsettled obligations landing inside the forecast window. */
    List<SupplierPayable> findBySettledFalseAndDueDateBetweenOrderByDueDateAsc(LocalDate from, LocalDate to);

    /** Anything unsettled whose date has already passed — still owed. */
    List<SupplierPayable> findBySettledFalseAndDueDateBeforeOrderByDueDateAsc(LocalDate date);

    List<SupplierPayable> findByBusinessOrderByDueDateAsc(BusinessType business);
}
