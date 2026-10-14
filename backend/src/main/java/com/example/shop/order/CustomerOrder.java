package com.example.shop.order;

import com.example.shop.auth.AppUser;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

/** An order paid on delivery (COD). Table is {@code orders} ({@code order} is reserved in SQL). */
@Entity
@Table(name = "orders")
public class CustomerOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status = OrderStatus.PENDING;

    @Column(name = "recipient_name", nullable = false, length = 100)
    private String recipientName;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(nullable = false, length = 500)
    private String address;

    @Column(length = 500)
    private String note;

    @Column(nullable = false)
    private long subtotal;

    @Column(name = "shipping_fee", nullable = false)
    private long shippingFee;

    @Column(nullable = false)
    private long total;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<OrderItem> items = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected CustomerOrder() {
    }

    public CustomerOrder(AppUser user, String recipientName, String phone, String address, String note) {
        this.user = user;
        this.recipientName = recipientName;
        this.phone = phone;
        this.address = address;
        this.note = note;
    }

    void addItem(OrderItem item) {
        item.setOrder(this);
        items.add(item);
    }

    void setTotals(long subtotal, long shippingFee) {
        this.subtotal = subtotal;
        this.shippingFee = shippingFee;
        this.total = subtotal + shippingFee;
    }

    void setStatus(OrderStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    /** Human-friendly order number shown to customers, e.g. DH000042. */
    public String getCode() {
        return "DH%06d".formatted(id);
    }

    public Long getId() {
        return id;
    }

    public AppUser getUser() {
        return user;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public String getPhone() {
        return phone;
    }

    public String getAddress() {
        return address;
    }

    public String getNote() {
        return note;
    }

    public long getSubtotal() {
        return subtotal;
    }

    public long getShippingFee() {
        return shippingFee;
    }

    public long getTotal() {
        return total;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
