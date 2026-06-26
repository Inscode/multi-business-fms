package com.multi.finance.repository;

import com.multi.finance.entity.ReturnProduct;
import com.multi.finance.enums.BusinessType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReturnProductRepository extends JpaRepository<ReturnProduct, Long> {
    List<ReturnProduct> findByBusinessAndActiveTrue(BusinessType business);
    List<ReturnProduct> findByBusiness(BusinessType business);
    List<ReturnProduct> findAllByOrderByBusinessAscNameAsc();
    List<ReturnProduct> findByBusinessAndActiveTrueAndIsStockProductTrue(BusinessType business);
    List<ReturnProduct> findByBusinessAndActiveTrueAndIsReturnProductTrue(BusinessType business);
}