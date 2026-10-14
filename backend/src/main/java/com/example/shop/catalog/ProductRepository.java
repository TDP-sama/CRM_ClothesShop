package com.example.shop.catalog;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    Optional<Product> findBySlugAndActiveTrue(String slug);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, Long id);

    boolean existsByCategoryId(Long categoryId);

    long countByActiveTrue();

    /** Rows of [categoryId, number of visible products]. */
    @Query("select p.category.id, count(p) from Product p where p.active = true group by p.category.id")
    List<Object[]> countActiveByCategory();

    @Query("""
            select p from Product p
            where p.active = true and p.category.id = :categoryId and p.id <> :excludeId
            order by p.featured desc, p.createdAt desc
            """)
    List<Product> findRelated(@Param("categoryId") Long categoryId, @Param("excludeId") Long excludeId, Pageable pageable);
}
