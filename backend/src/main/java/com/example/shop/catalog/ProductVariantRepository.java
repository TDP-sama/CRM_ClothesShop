package com.example.shop.catalog;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long>,
        JpaSpecificationExecutor<ProductVariant> {

    /** Locks the rows so two concurrent stock changes cannot both use the same count. Ordered to avoid deadlocks. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from ProductVariant v join fetch v.product where v.id in :ids order by v.id")
    List<ProductVariant> findAllForUpdate(@Param("ids") Collection<Long> ids);

    @Query("select distinct v.size from ProductVariant v where v.product.active = true")
    List<String> findActiveSizes();

    @Query("select distinct v.color from ProductVariant v where v.product.active = true order by v.color")
    List<String> findActiveColors();

    @Query("select v.sku from ProductVariant v where v.sku is not null")
    List<String> findAllSkus();

    boolean existsBySkuAndIdNot(String sku, Long id);

    @Query("select coalesce(sum(v.stock), 0) from ProductVariant v where v.product.active = true")
    long sumActiveStock();

    @Query("select count(v) from ProductVariant v where v.product.active = true")
    long countActive();

    @Query("""
            select count(v) from ProductVariant v
            where v.product.active = true and v.stock > 0 and v.stock <= v.lowStockThreshold
            """)
    long countActiveLowStock();

    @Query("select count(v) from ProductVariant v where v.product.active = true and v.stock = 0")
    long countActiveOutOfStock();
}
