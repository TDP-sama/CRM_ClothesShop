package com.example.shop.inventory;

/** Why a variant's stock changed. Every change of {@code ProductVariant.stock} is one of these. */
public enum MovementType {
    /** Stock a variant started with (when created, or what it had before inventory tracking existed). */
    INITIAL,
    /** Goods receipt (phiếu nhập). */
    RECEIPT,
    /** Sold: taken when a customer places an order. */
    SALE,
    /** Back on the shelf after an order was cancelled. */
    RETURN,
    /** Manual correction: stock count, damaged or lost goods. */
    ADJUSTMENT
}
