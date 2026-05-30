package com.multi.finance.repository;

import com.multi.finance.entity.ShadowStockMovement;
import com.multi.finance.entity.ReturnProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ShadowStockMovementRepository extends JpaRepository<ShadowStockMovement, Long> {

    List<ShadowStockMovement> findByProductOrderByCreatedAtDesc(ReturnProduct product);

    List<ShadowStockMovement> findByProductAndCancelledFalseOrderByCreatedAtDesc(ReturnProduct product);

    List<ShadowStockMovement> findByBillId(Long billId);

    List<ShadowStockMovement> findByBillIdAndCancelledFalse(Long billId);

    @Query("SELECT ssm FROM ShadowStockMovement ssm WHERE ssm.product = :product AND ssm.cancelled = false")
    List<ShadowStockMovement> findActiveMovementsByProduct(@Param("product") ReturnProduct product);

    @Query("SELECT SUM(CASE " +
           "  WHEN ssm.type IN ('STOCK_IN', 'SALABLE_RETURN') THEN ssm.quantity " +
           "  WHEN ssm.type IN ('BILL_OUT') THEN -ssm.quantity " +
           "  ELSE 0 END) " +
           "FROM ShadowStockMovement ssm " +
           "WHERE ssm.product = :product AND ssm.cancelled = false")
    Long getAvailableBalance(@Param("product") ReturnProduct product);

    @Query("SELECT SUM(CASE " +
           "  WHEN ssm.type = 'DAMAGE_IN' THEN ssm.quantity " +
           "  WHEN ssm.type = 'DAMAGE_TO_COMPANY' THEN -ssm.quantity " +
           "  ELSE 0 END) " +
           "FROM ShadowStockMovement ssm " +
           "WHERE ssm.product = :product AND ssm.cancelled = false")
    Long getDamageBalance(@Param("product") ReturnProduct product);
}
