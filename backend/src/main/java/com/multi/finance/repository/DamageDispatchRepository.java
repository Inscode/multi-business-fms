package com.multi.finance.repository;

import com.multi.finance.entity.DamageDispatch;
import com.multi.finance.enums.BusinessType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DamageDispatchRepository extends JpaRepository<DamageDispatch, Long> {

    List<DamageDispatch> findByBusinessOrderByCreatedAtDesc(BusinessType business);

    List<DamageDispatch> findAllByOrderByCreatedAtDesc();

    @Query("SELECT d FROM DamageDispatch d LEFT JOIN FETCH d.items i LEFT JOIN FETCH i.product LEFT JOIN FETCH d.enteredBy WHERE d.id = :id")
    Optional<DamageDispatch> findByIdWithItems(@Param("id") Long id);
}
