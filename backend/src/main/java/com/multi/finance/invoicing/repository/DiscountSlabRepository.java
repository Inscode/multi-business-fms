package com.multi.finance.invoicing.repository;

import com.multi.finance.invoicing.entity.DiscountSlab;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DiscountSlabRepository extends JpaRepository<DiscountSlab, Long> {
    List<DiscountSlab> findByBrandIdOrderBySortOrder(Long brandId);
}
