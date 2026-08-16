package com.multi.finance.repository;

import com.multi.finance.entity.RouteArea;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RouteAreaRepository extends JpaRepository<RouteArea, Long> {
    List<RouteArea> findByActiveTrueOrderBySortOrderAscNameAsc();
    List<RouteArea> findAllByOrderBySortOrderAscNameAsc();
    Optional<RouteArea> findByNameIgnoreCase(String name);
}
