package com.example.shop.inventory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/** Request and response shapes of the inventory API. */
public final class InventoryDtos {

    private InventoryDtos() {
    }

    /** IN_STOCK above the threshold, LOW at or below it, OUT at zero. */
    public enum StockStatus {
        IN_STOCK,
        LOW,
        OUT
    }

    /** Reasons for a manual adjustment. */
    public enum AdjustmentReason {
        COUNT,
        DAMAGED,
        LOST,
        OTHER
    }

    public record InventoryItem(Long variantId, Long productId, String productName, String productSlug,
            boolean productActive, String imageUrl, String categoryName, String sku, String size, String color,
            int stock, int lowStockThreshold, StockStatus status) {
    }

    public record Summary(long totalUnits, long variants, long lowStock, long outOfStock) {
    }

    public record MovementDto(Long id, Instant createdAt, MovementType type, Long variantId, Long productId,
            String productName, String sku, String size, String color, int quantityChange, int stockBefore,
            int stockAfter, String reason, String note, String referenceCode, String actorEmail) {

        static MovementDto of(StockMovement m) {
            return new MovementDto(m.getId(), m.getCreatedAt(), m.getType(), m.getVariantId(), m.getProductId(),
                    m.getProductName(), m.getSku(), m.getSize(), m.getColor(), m.getQuantityChange(),
                    m.getStockBefore(), m.getStockAfter(), m.getReason(), m.getNote(), m.getReferenceCode(),
                    m.getActorEmail());
        }
    }

    public record ReceiptLineDto(Long variantId, String productName, String sku, String size, String color,
            int quantity) {
    }

    public record ReceiptDto(Long id, String code, String supplier, String note, String createdBy, Instant createdAt,
            int totalQuantity, List<ReceiptLineDto> lines) {

        static ReceiptDto of(GoodsReceipt r) {
            List<ReceiptLineDto> lines = r.getLines().stream()
                    .map(l -> new ReceiptLineDto(l.getVariantId(), l.getProductName(), l.getSku(), l.getSize(),
                            l.getColor(), l.getQuantity()))
                    .toList();
            return new ReceiptDto(r.getId(), r.getCode(), r.getSupplier(), r.getNote(), r.getCreatedBy(),
                    r.getCreatedAt(), lines.stream().mapToInt(ReceiptLineDto::quantity).sum(), lines);
        }
    }

    public record ReceiptLineRequest(
            @NotNull(message = "Vui lòng chọn sản phẩm") Long variantId,
            @NotNull(message = "Vui lòng nhập số lượng") @Min(value = 1, message = "Số lượng nhập tối thiểu là 1")
            @Max(value = 100_000, message = "Số lượng quá lớn") Integer quantity) {
    }

    public record ReceiptRequest(
            @Size(max = 200, message = "Tên nhà cung cấp tối đa 200 ký tự") String supplier,
            @Size(max = 500, message = "Ghi chú tối đa 500 ký tự") String note,
            @NotEmpty(message = "Phiếu nhập cần ít nhất một dòng") @Size(max = 200, message = "Phiếu nhập quá nhiều dòng")
            List<@Valid ReceiptLineRequest> lines) {
    }

    public record AdjustmentRequest(
            @NotNull(message = "Vui lòng chọn sản phẩm") Long variantId,
            @NotNull(message = "Vui lòng nhập số lượng thực tế") @Min(value = 0, message = "Số lượng không được âm")
            @Max(value = 1_000_000, message = "Số lượng quá lớn") Integer actualQuantity,
            @NotNull(message = "Vui lòng chọn lý do") AdjustmentReason reason,
            @Size(max = 500, message = "Ghi chú tối đa 500 ký tự") String note) {
    }

    public record SettingsUpdate(
            @Size(max = 64, message = "SKU tối đa 64 ký tự") String sku,
            @NotNull(message = "Vui lòng nhập ngưỡng cảnh báo") @Min(value = 0, message = "Ngưỡng không được âm")
            @Max(value = 100_000, message = "Ngưỡng quá lớn") Integer lowStockThreshold) {
    }
}
