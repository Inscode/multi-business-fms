package com.multi.finance.invoicing.repository;

import com.multi.finance.invoicing.entity.Item;
import com.multi.finance.invoicing.enums.CategoryType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ItemRepository extends JpaRepository<Item, Long> {
    List<Item> findByCategoryAndActiveTrue(CategoryType category);
    List<Item> findByBrandIdAndActiveTrue(Long brandId);
    Optional<Item> findByItemCode(String itemCode);
    List<Item> findByItemCodeStartingWith(String prefix);

    @Query("SELECT i FROM Item i JOIN FETCH i.brand WHERE i.category = :category AND i.active = true ORDER BY i.brand.name, i.description")
    List<Item> findByCategoryWithBrand(@Param("category") CategoryType category);

    // FMS runs with open-in-view=false, so the brand must be fetched eagerly for list mapping
    @Query("SELECT i FROM Item i JOIN FETCH i.brand ORDER BY i.brand.name, i.description")
    List<Item> findAllWithBrand();

    @Modifying
    @Query("UPDATE Item i SET i.stockQty = i.stockQty + :delta WHERE i.id = :id")
    void adjustStock(@Param("id") Long id, @Param("delta") int delta);
}
