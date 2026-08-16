package com.multi.finance.repository;

import com.multi.finance.entity.ReturnImage;
import com.multi.finance.enums.ReturnType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReturnImageRepository extends JpaRepository<ReturnImage, Long> {

    List<ReturnImage> findByBillReturnIdOrderByPageNoAscIdAsc(Long billReturnId);

    /** The book pages for one round, damage and salable kept apart. */
    List<ReturnImage> findByDeliveryRunIdAndReturnTypeOrderByPageNoAscIdAsc(
            Long deliveryRunId, ReturnType returnType);

    List<ReturnImage> findByDeliveryRunIdOrderByPageNoAscIdAsc(Long deliveryRunId);

    long countByBillReturnId(Long billReturnId);

    long countByDeliveryRunIdAndReturnType(Long deliveryRunId, ReturnType returnType);
}
