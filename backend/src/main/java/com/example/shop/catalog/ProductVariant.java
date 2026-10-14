package com.example.shop.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.ColumnDefault;

/** One purchasable size/color combination of a product, with its own stock. */
@Entity
@Table(name = "product_variants",
        uniqueConstraints = @UniqueConstraint(columnNames = {"product_id", "size", "color"}))
public class ProductVariant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(nullable = false, length = 20)
    private String size;

    @Column(nullable = false, length = 40)
    private String color;

    /**
     * Changed only through {@code InventoryService}, which records every change as a stock movement;
     * orders and cancellations go through it too.
     */
    @Column(nullable = false)
    private int stock;

    /** Stock-keeping unit, generated from product/color/size; nullable only for rows created before SKUs. */
    @Column(unique = true, length = 64)
    private String sku;

    /** At or below this stock (and above 0) the variant counts as "running low". */
    @ColumnDefault("5")
    @Column(name = "low_stock_threshold", nullable = false)
    private int lowStockThreshold = 5;

    protected ProductVariant() {
    }

    ProductVariant(Product product, String size, String color, int stock) {
        this.product = product;
        this.size = size;
        this.color = color;
        this.stock = stock;
    }

    public Long getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public String getSize() {
        return size;
    }

    void setSize(String size) {
        this.size = size;
    }

    public String getColor() {
        return color;
    }

    void setColor(String color) {
        this.color = color;
    }

    public int getStock() {
        return stock;
    }

    public void setStock(int stock) {
        this.stock = stock;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public int getLowStockThreshold() {
        return lowStockThreshold;
    }

    public void setLowStockThreshold(int lowStockThreshold) {
        this.lowStockThreshold = lowStockThreshold;
    }
}
