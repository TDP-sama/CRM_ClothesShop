package com.example.shop.order;

/**
 * Published by {@link OrderService} inside its transaction; listeners that talk to the outside world (email)
 * should use {@code @TransactionalEventListener} so they only act once the change is committed.
 */
public final class OrderEvents {

    private OrderEvents() {
    }

    public record Placed(Long orderId) {
    }

    public record StatusChanged(Long orderId, OrderStatus status) {
    }
}
