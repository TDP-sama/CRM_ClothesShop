package com.example.shop.order;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<CustomerOrder, Long> {

    Page<CustomerOrder> findByUserId(Long userId, Pageable pageable);

    Optional<CustomerOrder> findByIdAndUserId(Long id, Long userId);

    @EntityGraph(attributePaths = "user")
    Page<CustomerOrder> findByStatus(OrderStatus status, Pageable pageable);

    @EntityGraph(attributePaths = "user")
    Page<CustomerOrder> findAllBy(Pageable pageable);

    long countByStatus(OrderStatus status);

    /** Orders placed in [from, to) except those with the excluded status (cancelled ones, for sales reports). */
    @Query("select o from CustomerOrder o where o.createdAt >= :from and o.createdAt < :to and o.status <> :excluded")
    List<CustomerOrder> findPlacedBetween(@Param("from") Instant from, @Param("to") Instant to,
            @Param("excluded") OrderStatus excluded);

    @Query("select coalesce(sum(o.total), 0) from CustomerOrder o where o.status = :status")
    long sumTotalByStatus(@Param("status") OrderStatus status);

    /** Whether the user has received this product, which is what allows them to review it. */
    @Query("""
            select count(i) > 0 from OrderItem i
            where i.order.user.id = :userId and i.productId = :productId and i.order.status = :status
            """)
    boolean hasPurchased(@Param("userId") Long userId, @Param("productId") Long productId,
            @Param("status") OrderStatus status);
}
