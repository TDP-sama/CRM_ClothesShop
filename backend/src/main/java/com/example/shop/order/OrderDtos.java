package com.example.shop.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/** Request and response shapes of the order API. */
public final class OrderDtos {

    private OrderDtos() {
    }

    public record CartItem(
            @NotNull Long variantId,
            @NotNull @Min(value = 1, message = "Số lượng tối thiểu là 1")
            @Max(value = 20, message = "Mỗi sản phẩm tối đa 20 cái") Integer quantity) {
    }

    public record PlaceOrderRequest(
            @NotEmpty(message = "Giỏ hàng đang trống") @Size(max = 50, message = "Giỏ hàng quá nhiều sản phẩm")
            List<@Valid CartItem> items,
            @NotBlank(message = "Vui lòng nhập tên người nhận") @Size(max = 100) String recipientName,
            @NotBlank(message = "Vui lòng nhập số điện thoại")
            @Pattern(regexp = "^(0|\\+84)\\d{9,10}$", message = "Số điện thoại không hợp lệ") String phone,
            @NotBlank(message = "Vui lòng nhập địa chỉ giao hàng") @Size(max = 500, message = "Địa chỉ quá dài")
            String address,
            @Size(max = 500, message = "Ghi chú tối đa 500 ký tự") String note) {

        public PlaceOrderRequest {
            recipientName = recipientName == null ? null : recipientName.trim();
            phone = phone == null ? null : phone.replaceAll("[\\s.-]", "");
            address = address == null ? null : address.trim();
            note = note == null || note.isBlank() ? null : note.trim();
        }
    }

    public record StatusUpdate(@NotNull(message = "Vui lòng chọn trạng thái") OrderStatus status) {
    }

    public record OrderItemDto(Long productId, String productSlug, String productName, String imageUrl,
            String size, String color, long unitPrice, int quantity, long lineTotal) {
    }

    public record OrderDto(Long id, String code, OrderStatus status, List<OrderStatus> nextStatuses,
            boolean cancellable, String customerEmail, String recipientName, String phone, String address,
            String note, String paymentMethod, long subtotal, long shippingFee, long total, int itemCount,
            List<OrderItemDto> items, Instant createdAt, Instant updatedAt) {
    }

    public record ShopConfig(long shippingFee, long freeShippingThreshold) {
    }

    /** {@code lowStockVariants}: variants of products on sale that are running low or sold out. */
    public record Stats(long activeProducts, long customers, long orders, long pendingOrders, long deliveredRevenue,
            long lowStockVariants) {
    }
}
