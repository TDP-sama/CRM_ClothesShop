package com.example.shop.review;

import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    interface RatingRow {
        Long getProductId();

        Double getAverage();

        Long getCount();
    }

    @Query("""
            select r.product.id as productId, avg(r.rating) as average, count(r) as count
            from Review r where r.product.id in :productIds group by r.product.id
            """)
    List<RatingRow> ratingsFor(@Param("productIds") Collection<Long> productIds);

    @EntityGraph(attributePaths = "user")
    Page<Review> findByProductIdOrderByCreatedAtDesc(Long productId, Pageable pageable);

    boolean existsByProductIdAndUserId(Long productId, Long userId);
}
