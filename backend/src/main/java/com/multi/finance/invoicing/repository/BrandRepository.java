package com.multi.finance.invoicing.repository;

import com.multi.finance.invoicing.entity.Brand;
import com.multi.finance.invoicing.enums.CategoryType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface BrandRepository extends JpaRepository<Brand, Long> {
    List<Brand> findByCategoryAndActiveTrue(CategoryType category);

    @Query("SELECT b FROM Brand b LEFT JOIN FETCH b.slabs WHERE b.active = true")
    List<Brand> findAllActiveWithSlabs();

    @Query("SELECT b FROM Brand b LEFT JOIN FETCH b.slabs WHERE b.category = :category AND b.active = true")
    List<Brand> findByCategoryWithSlabs(CategoryType category);
}
