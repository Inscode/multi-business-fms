package com.multi.finance.repository;

import com.multi.finance.entity.ShadowStockMovement;
import com.multi.finance.entity.ReturnProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Repository
public interface ShadowStockMovementRepository extends JpaRepository<ShadowStockMovement, Long> {

    List<ShadowStockMovement> findByProductOrderByCreatedAtDesc(ReturnProduct product);

    List<ShadowStockMovement> findByProductAndCancelledFalseOrderByCreatedAtDesc(ReturnProduct product);

    List<ShadowStockMovement> findByBillId(Long billId);

    List<ShadowStockMovement> findByBillIdAndCancelledFalse(Long billId);

    List<ShadowStockMovement> findByBillIdAndProductIdAndCancelledFalse(Long billId, Long productId);

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

    /** IDs of bills that have at least one active (non-cancelled) BILL_OUT movement — avoids loading all movements. */
    @Query("SELECT DISTINCT m.bill.id FROM ShadowStockMovement m WHERE m.bill IS NOT NULL AND m.cancelled = false")
    Set<Long> findBillIdsWithActiveMovements();

    /**
     * Bulk balance query for a list of product IDs — avoids N+1 in stock checks.
     * Returns [productId, availableQty] rows.
     */
    @Query("SELECT m.product.id, " +
           "COALESCE(SUM(CASE WHEN m.cancelled = false AND m.type IN ('STOCK_IN','SALABLE_RETURN') THEN m.quantity " +
           "               WHEN m.cancelled = false AND m.type = 'BILL_OUT' THEN -m.quantity ELSE 0 END), 0) " +
           "FROM ShadowStockMovement m WHERE m.product.id IN :productIds GROUP BY m.product.id")
    List<Object[]> getAvailableBalancesForProducts(@Param("productIds") List<Long> productIds);

    /**
     * Single aggregate query returning [productId, availableQty, damageQty] for ALL RAINCO products.
     * Replaces N+1 calls to getAvailableBalance + getDamageBalance per product.
     */
    @Query("SELECT p.id, " +
           "COALESCE(SUM(CASE WHEN m.cancelled = false AND m.type IN ('STOCK_IN','SALABLE_RETURN') THEN m.quantity " +
           "                  WHEN m.cancelled = false AND m.type = 'BILL_OUT' THEN -m.quantity ELSE 0 END), 0), " +
           "COALESCE(SUM(CASE WHEN m.cancelled = false AND m.type = 'DAMAGE_IN' THEN m.quantity " +
           "                  WHEN m.cancelled = false AND m.type = 'DAMAGE_TO_COMPANY' THEN -m.quantity ELSE 0 END), 0) " +
           "FROM ReturnProduct p LEFT JOIN ShadowStockMovement m ON m.product = p " +
           "WHERE p.business = 'RAINCO' GROUP BY p.id")
    List<Object[]> getAggregatedBalancesForRainco();

    /** Returns [productId, damageQty] for all products of a given business that have damage balance > 0. */
    @Query("SELECT p.id, " +
           "COALESCE(SUM(CASE WHEN m.cancelled = false AND m.type = 'DAMAGE_IN' THEN m.quantity " +
           "                  WHEN m.cancelled = false AND m.type = 'DAMAGE_TO_COMPANY' THEN -m.quantity " +
           "                  ELSE 0 END), 0) AS damageQty " +
           "FROM ReturnProduct p LEFT JOIN ShadowStockMovement m ON m.product = p " +
           "WHERE p.business = :business AND p.isReturnProduct = true " +
           "GROUP BY p.id HAVING COALESCE(SUM(CASE WHEN m.cancelled = false AND m.type = 'DAMAGE_IN' THEN m.quantity " +
           "                                        WHEN m.cancelled = false AND m.type = 'DAMAGE_TO_COMPANY' THEN -m.quantity " +
           "                                        ELSE 0 END), 0) > 0")
    List<Object[]> getDamageStockByBusiness(@Param("business") com.multi.finance.enums.BusinessType business);
}
