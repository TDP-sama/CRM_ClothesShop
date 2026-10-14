package com.example.shop.catalog;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/** Request and response shapes of the catalog API. */
public final class CatalogDtos {

    private CatalogDtos() {
    }

    // ---- Storefront ----

    public record CategoryDto(Long id, String name, String slug, String imageUrl, int sortOrder, long productCount) {
    }

    public record ProductSummary(Long id, String slug, String name, long price, Long compareAtPrice,
            String imageUrl, String hoverImageUrl, String categoryName, List<String> colors, List<String> sizes,
            boolean inStock, double ratingAverage, long ratingCount, Instant createdAt) {
    }

    public record VariantDto(Long id, String size, String color, int stock) {
    }

    public record CategoryRef(String name, String slug) {
    }

    public record ProductDetail(Long id, String slug, String name, String description, long price,
            Long compareAtPrice, List<String> images, CategoryRef category, List<String> sizes, List<String> colors,
            List<VariantDto> variants, double ratingAverage, long ratingCount) {
    }

    public record Filters(List<String> sizes, List<String> colors) {
    }

    // ---- Admin ----

    public record AdminProductRow(Long id, String name, String slug, String imageUrl, String categoryName,
            long price, Long compareAtPrice, int totalStock, boolean active, boolean featured) {
    }

    public record AdminVariantDto(Long id, String size, String color, int stock, String sku) {
    }

    public record AdminProduct(Long id, String name, String slug, String description, long price,
            Long compareAtPrice, Long categoryId, List<String> images, List<AdminVariantDto> variants,
            boolean active, boolean featured) {
    }

    /** {@code stock} is the initial stock of a new variant; it is ignored for existing ones (use the inventory). */
    public record VariantUpsert(
            Long id,
            @NotBlank(message = "Vui lòng nhập size") @Size(max = 20, message = "Size tối đa 20 ký tự") String size,
            @NotBlank(message = "Vui lòng nhập màu") @Size(max = 40, message = "Màu tối đa 40 ký tự") String color,
            @PositiveOrZero(message = "Tồn kho không được âm") Integer stock) {

        public VariantUpsert {
            size = size == null ? null : size.trim();
            color = color == null ? null : color.trim();
        }
    }

    public record ProductUpsert(
            @NotBlank(message = "Vui lòng nhập tên sản phẩm") @Size(max = 200, message = "Tên tối đa 200 ký tự") String name,
            @Size(max = 220) String slug,
            @Size(max = 5000, message = "Mô tả tối đa 5000 ký tự") String description,
            @NotNull(message = "Vui lòng nhập giá") @Positive(message = "Giá phải lớn hơn 0") Long price,
            @Positive(message = "Giá gốc phải lớn hơn 0") Long compareAtPrice,
            @NotNull(message = "Vui lòng chọn danh mục") Long categoryId,
            @Size(max = 10, message = "Tối đa 10 ảnh") List<@NotBlank @Size(max = 1000) String> images,
            @NotEmpty(message = "Cần ít nhất một biến thể size/màu") @Size(max = 100) List<@Valid VariantUpsert> variants,
            boolean active,
            boolean featured) {
    }

    public record CategoryUpsert(
            @NotBlank(message = "Vui lòng nhập tên danh mục") @Size(max = 100) String name,
            @Size(max = 120) String slug,
            @Size(max = 1000) String imageUrl,
            @Min(0) @Max(10_000) int sortOrder) {
    }
}
