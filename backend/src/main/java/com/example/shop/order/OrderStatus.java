package com.example.shop.order;

import java.util.Set;

/** Order lifecycle. Cancelling returns the items to stock; DELIVERED and CANCELLED are final. */
public enum OrderStatus {
    PENDING,
    CONFIRMED,
    SHIPPING,
    DELIVERED,
    CANCELLED;

    public Set<OrderStatus> next() {
        return switch (this) {
            case PENDING -> Set.of(CONFIRMED, CANCELLED);
            case CONFIRMED -> Set.of(SHIPPING, CANCELLED);
            case SHIPPING -> Set.of(DELIVERED, CANCELLED);
            case DELIVERED, CANCELLED -> Set.of();
        };
    }
}
