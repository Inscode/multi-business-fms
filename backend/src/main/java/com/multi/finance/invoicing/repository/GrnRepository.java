package com.multi.finance.invoicing.repository;

import com.multi.finance.invoicing.entity.GoodsReceivedNote;
import com.multi.finance.invoicing.enums.GrnStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GrnRepository extends JpaRepository<GoodsReceivedNote, Long> {

    // open-in-view is off — lines and items must be fetched eagerly for mapping
    @Query("SELECT DISTINCT g FROM GoodsReceivedNote g LEFT JOIN FETCH g.lines l " +
           "LEFT JOIN FETCH l.item WHERE g.status = :status ORDER BY g.createdAt DESC")
    List<GoodsReceivedNote> findByStatusWithLines(@Param("status") GrnStatus status);

    @Query("SELECT DISTINCT g FROM GoodsReceivedNote g LEFT JOIN FETCH g.lines l " +
           "LEFT JOIN FETCH l.item ORDER BY g.createdAt DESC")
    List<GoodsReceivedNote> findAllWithLines();

    @Query("SELECT g FROM GoodsReceivedNote g LEFT JOIN FETCH g.lines l " +
           "LEFT JOIN FETCH l.item WHERE g.id = :id")
    Optional<GoodsReceivedNote> findByIdWithDetails(@Param("id") Long id);

    @Query(value = "SELECT NEXTVAL('seq_inv_grn')", nativeQuery = true)
    Long nextSequenceValue();
}
