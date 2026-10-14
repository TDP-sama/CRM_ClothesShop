package com.example.shop.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * A line of an order. Product data is copied at purchase time and product/variant ids are plain columns
 * (no foreign keys), so later catalog edits never change or break past orders.
 */
@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id")
    private CustomerOrder order;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "variant_id", nullable = false)
    private Long variantId;

    @Column(name = "product_name", nullable = false, length = 200)
    private String productName;

    @Column(name = "product_slug", nullable = false, length = 220)
    private String productSlug;

    @Column(name = "image_url", length = 1000)
    private String imageUrl;

    @Column(nullable = false, length = 20)
    private String size;

    @Column(nullable = false, length = 40)
    private String color;

    @Column(name = "unit_price", nullable = false)
    private long unitPrice;

    @Column(nullable = false)
    private int quantity;

    protected OrderItem() {
    }

    OrderItem(Long productId, Long variantId, String productName, String productSlug, String imageUrl,
            String size, String color, long unitPrice, int quantity) {
        this.productId = productId;
        this.variantId = variantId;
        this.productName = productName;
        this.productSlug = productSlug;
        this.imageUrl = imageUrl;
        this.size = size;
        this.color = color;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
    }

    void setOrder(CustomerOrder order) {
        this.order = order;
    }

    public long getLineTotal() {
        return unitPrice * quantity;
    }

    public Long getId() {
        return id;
    }

    public CustomerOrder getOrder() {
        return order;
    }

    public Long getProductId() {
        return productId;
    }

    public Long getVariantId() {
        return variantId;
    }

    public String getProductName() {
        return productName;
    }

    public String getProductSlug() {
        return productSlug;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public String getSize() {
        return size;
    }

    public String getColor() {
        return color;
    }

    public long getUnitPrice() {
        return unitPrice;
    }

    public int getQuantity() {
        return quantity;
    }
}
