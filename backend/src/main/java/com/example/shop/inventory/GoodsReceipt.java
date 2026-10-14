package com.example.shop.inventory;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** A goods receipt (phiếu nhập kho): goods received from a supplier, possibly for several variants. */
@Entity
@Table(name = "goods_receipts")
public class GoodsReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 200)
    private String supplier;

    @Column(length = 500)
    private String note;

    @Column(name = "created_by", nullable = false, length = 254)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "receipt", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<Line> lines = new ArrayList<>();

    protected GoodsReceipt() {
    }

    GoodsReceipt(String supplier, String note, String createdBy) {
        this.supplier = supplier;
        this.note = note;
        this.createdBy = createdBy;
    }

    void addLine(Long variantId, String productName, String sku, String size, String color, int quantity) {
        lines.add(new Line(this, variantId, productName, sku, size, color, quantity));
    }

    /** Receipt number shown to staff, e.g. PN000012. */
    public String getCode() {
        return "PN%06d".formatted(id);
    }

    public Long getId() { return id; }
    public String getSupplier() { return supplier; }
    public String getNote() { return note; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public List<Line> getLines() { return lines; }

    /** One received variant. Product data is copied, like order items and stock movements. */
    @Entity
    @Table(name = "goods_receipt_lines")
    public static class Line {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "receipt_id")
        private GoodsReceipt receipt;

        @Column(name = "variant_id", nullable = false)
        private Long variantId;

        @Column(name = "product_name", nullable = false, length = 200)
        private String productName;

        @Column(length = 64)
        private String sku;

        @Column(nullable = false, length = 20)
        private String size;

        @Column(nullable = false, length = 40)
        private String color;

        @Column(nullable = false)
        private int quantity;

        protected Line() {
        }

        Line(GoodsReceipt receipt, Long variantId, String productName, String sku, String size, String color,
                int quantity) {
            this.receipt = receipt;
            this.variantId = variantId;
            this.productName = productName;
            this.sku = sku;
            this.size = size;
            this.color = color;
            this.quantity = quantity;
        }

        public Long getVariantId() { return variantId; }
        public String getProductName() { return productName; }
        public String getSku() { return sku; }
        public String getSize() { return size; }
        public String getColor() { return color; }
        public int getQuantity() { return quantity; }
    }
}
