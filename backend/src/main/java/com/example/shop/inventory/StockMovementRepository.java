package com.example.shop.inventory;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long>,
        JpaSpecificationExecutor<StockMovement> {

    /** Variants among the given ids that already have at least one movement. */
    @Query("select distinct m.variantId from StockMovement m where m.variantId in :ids")
    List<Long> findTrackedVariantIds(@Param("ids") Collection<Long> ids);
}
