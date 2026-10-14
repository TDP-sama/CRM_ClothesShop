package com.example.shop.catalog;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A product as shown in the store. Prices are whole Vietnamese đồng. Products are never hard-deleted
 * (orders keep referring to them); admins hide them with {@code active = false}.
 */
@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    /** ASCII form of the name; used in URLs and for accent-insensitive search. */
    @Column(nullable = false, unique = true, length = 220)
    private String slug;

    @Column(length = 5000)
    private String description;

    /** Selling price. */
    @Column(nullable = false)
    private long price;

    /** Original price shown struck through when the product is on sale; null when not on sale. */
    @Column(name = "compare_at_price")
    private Long compareAtPrice;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id")
    private Category category;

    @ElementCollection
    @CollectionTable(name = "product_images", joinColumns = @JoinColumn(name = "product_id"))
    @OrderColumn(name = "position")
    @Column(name = "url", nullable = false, length = 1000)
    private List<String> images = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<ProductVariant> variants = new ArrayList<>();

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private boolean featured;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Product() {
    }

    public Product(String name, String slug, Category category) {
        this.name = name;
        this.slug = slug;
        this.category = category;
    }

    public ProductVariant addVariant(String size, String color, int stock) {
        ProductVariant variant = new ProductVariant(this, size, color, stock);
        variants.add(variant);
        return variant;
    }

    public boolean isOnSale() {
        return compareAtPrice != null && compareAtPrice > price;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public long getPrice() {
        return price;
    }

    public void setPrice(long price) {
        this.price = price;
    }

    public Long getCompareAtPrice() {
        return compareAtPrice;
    }

    public void setCompareAtPrice(Long compareAtPrice) {
        this.compareAtPrice = compareAtPrice;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public List<String> getImages() {
        return images;
    }

    public List<ProductVariant> getVariants() {
        return variants;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isFeatured() {
        return featured;
    }

    public void setFeatured(boolean featured) {
        this.featured = featured;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
