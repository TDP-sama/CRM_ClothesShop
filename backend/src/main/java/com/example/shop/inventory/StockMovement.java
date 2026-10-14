package com.example.shop.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * One line of the stock ledger ("thẻ kho"). Like order items, it keeps the variant id as a plain column plus a
 * copy of the product data, so the history survives later edits or removal of the variant.
 */
@Entity
@Table(name = "stock_movements", indexes = {
        @Index(name = "idx_stock_movements_variant", columnList = "variant_id"),
        @Index(name = "idx_stock_movements_created", columnList = "created_at")})
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "variant_id", nullable = false)
    private Long variantId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_name", nullable = false, length = 200)
    private String productName;

    @Column(length = 64)
    private String sku;

    @Column(nullable = false, length = 20)
    private String size;

    @Column(nullable = false, length = 40)
    private String color;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MovementType type;

    /** Signed change: positive adds stock, negative removes it. */
    @Column(name = "quantity_change", nullable = false)
    private int quantityChange;

    @Column(name = "stock_before", nullable = false)
    private int stockBefore;

    @Column(name = "stock_after", nullable = false)
    private int stockAfter;

    /** For adjustments: COUNT, DAMAGED, LOST or OTHER. */
    @Column(length = 20)
    private String reason;

    @Column(length = 500)
    private String note;

    /** Related document, e.g. PN000012 (receipt) or DH000034 (order). */
    @Column(name = "reference_code", length = 20)
    private String referenceCode;

    /** Email of the account that caused the change (the customer for sales). */
    @Column(name = "actor_email", length = 254)
    private String actorEmail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected StockMovement() {
    }

    StockMovement(Long variantId, Long productId, String productName, String sku, String size, String color,
            MovementType type, int stockBefore, int stockAfter, String reason, String note, String referenceCode,
            String actorEmail) {
        this.variantId = variantId;
        this.productId = productId;
        this.productName = productName;
        this.sku = sku;
        this.size = size;
        this.color = color;
        this.type = type;
        this.quantityChange = stockAfter - stockBefore;
        this.stockBefore = stockBefore;
        this.stockAfter = stockAfter;
        this.reason = reason;
        this.note = note;
        this.referenceCode = referenceCode;
        this.actorEmail = actorEmail;
    }

    public Long getId() { return id; }
    public Long getVariantId() { return variantId; }
    public Long getProductId() { return productId; }
    public String getProductName() { return productName; }
    public String getSku() { return sku; }
    public String getSize() { return size; }
    public String getColor() { return color; }
    public MovementType getType() { return type; }
    public int getQuantityChange() { return quantityChange; }
    public int getStockBefore() { return stockBefore; }
    public int getStockAfter() { return stockAfter; }
    public String getReason() { return reason; }
    public String getNote() { return note; }
    public String getReferenceCode() { return referenceCode; }
    public String getActorEmail() { return actorEmail; }
    public Instant getCreatedAt() { return createdAt; }
}
