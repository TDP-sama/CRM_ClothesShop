package com.example.shop.catalog;

import com.example.shop.catalog.CatalogDtos.AdminProduct;
import com.example.shop.catalog.CatalogDtos.AdminProductRow;
import com.example.shop.catalog.CatalogDtos.CategoryDto;
import com.example.shop.catalog.CatalogDtos.CategoryUpsert;
import com.example.shop.catalog.CatalogDtos.ProductUpsert;
import com.example.shop.catalog.CatalogDtos.AdminVariantDto;
import com.example.shop.catalog.CatalogDtos.VariantUpsert;
import com.example.shop.common.PageResponse;
import com.example.shop.common.Slugs;
import com.example.shop.inventory.InventoryService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Catalog management for the admin pages. Products are hidden, never deleted, so order history stays intact. */
@Service
@Transactional
public class AdminCatalogService {

    /** Slugs that would be shadowed by fixed routes under {@code /api/products/}. */
    private static final Set<String> RESERVED_SLUGS = Set.of("filters");

    private final CategoryRepository categories;
    private final ProductRepository products;
    private final InventoryService inventory;

    public AdminCatalogService(CategoryRepository categories, ProductRepository products, InventoryService inventory) {
        this.categories = categories;
        this.products = products;
        this.inventory = inventory;
    }

    // ---- Products ----

    @Transactional(readOnly = true)
    public PageResponse<AdminProductRow> list(String q, int page, int size) {
        Specification<Product> spec = (root, query, cb) -> {
            if (q == null || q.isBlank()) {
                return cb.conjunction();
            }
            String like = "%" + Slugs.slugify(q) + "%";
            return cb.like(root.get("slug"), like);
        };
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100),
                Sort.by("createdAt").descending().and(Sort.by("id").descending()));
        return PageResponse.of(products.findAll(spec, pageRequest), p -> new AdminProductRow(p.getId(), p.getName(),
                p.getSlug(), p.getImages().isEmpty() ? null : p.getImages().get(0), p.getCategory().getName(),
                p.getPrice(), p.getCompareAtPrice(), p.getVariants().stream().mapToInt(ProductVariant::getStock).sum(),
                p.isActive(), p.isFeatured()));
    }

    @Transactional(readOnly = true)
    public AdminProduct get(Long id) {
        return toAdmin(product(id));
    }

    public AdminProduct create(ProductUpsert in, String actorEmail) {
        String slug = in.slug() == null || in.slug().isBlank()
                ? uniqueSlug(Slugs.slugify(in.name()), slugTaken(null))
                : requireFreeSlug(Slugs.slugify(in.slug()), null);
        Product p = new Product(in.name().trim(), slug, category(in.categoryId()));
        Map<ProductVariant, Integer> created = apply(p, in);
        products.saveAndFlush(p);
        openNewVariants(created, actorEmail);
        return toAdmin(p);
    }

    public AdminProduct update(Long id, ProductUpsert in, String actorEmail) {
        Product p = product(id);
        if (in.slug() != null && !in.slug().isBlank()) {
            p.setSlug(requireFreeSlug(Slugs.slugify(in.slug()), id));
        }
        p.setName(in.name().trim());
        p.setCategory(category(in.categoryId()));
        Map<ProductVariant, Integer> created = apply(p, in);
        products.saveAndFlush(p);
        openNewVariants(created, actorEmail);
        return toAdmin(p);
    }

    /** New variants start at 0 and receive their initial stock through the stock ledger (after flush: needs ids). */
    private void openNewVariants(Map<ProductVariant, Integer> created, String actorEmail) {
        created.forEach((variant, initialStock) -> inventory.open(variant, initialStock, actorEmail));
    }

    /** "Delete" from the admin UI: hides the product from the store but keeps it for existing orders. */
    public void hide(Long id) {
        product(id).setActive(false);
    }

    private Map<ProductVariant, Integer> apply(Product p, ProductUpsert in) {
        if (in.compareAtPrice() != null && in.compareAtPrice() <= in.price()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Giá gốc phải lớn hơn giá bán");
        }
        p.setDescription(in.description() == null ? null : in.description().trim());
        p.setPrice(in.price());
        p.setCompareAtPrice(in.compareAtPrice());
        p.setActive(in.active());
        p.setFeatured(in.featured());
        p.getImages().clear();
        if (in.images() != null) {
            in.images().stream().map(String::trim).distinct().forEach(p.getImages()::add);
        }
        return reconcileVariants(p, in.variants());
    }

    /**
     * Updates variants in place (matched by id, else by size+color) so their ids survive edits, adds new ones and
     * removes the rest. Orders and the stock ledger store variant ids as plain values, so removing a variant never
     * breaks them. The stock of existing variants is not touched here: it only changes through the inventory.
     *
     * @return the newly created variants with the initial stock requested for each
     */
    private static Map<ProductVariant, Integer> reconcileVariants(Product p, List<VariantUpsert> requested) {
        Set<String> seen = new HashSet<>();
        for (VariantUpsert v : requested) {
            if (!seen.add(key(v.size(), v.color()))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Biến thể bị trùng: size %s, màu %s".formatted(v.size(), v.color()));
            }
        }
        Map<Long, ProductVariant> byId = new HashMap<>();
        Map<String, ProductVariant> byKey = new HashMap<>();
        for (ProductVariant v : p.getVariants()) {
            byId.put(v.getId(), v);
            byKey.put(key(v.getSize(), v.getColor()), v);
        }
        List<ProductVariant> keep = new ArrayList<>();
        Map<ProductVariant, Integer> created = new LinkedHashMap<>();
        for (VariantUpsert in : requested) {
            ProductVariant v = in.id() != null ? byId.get(in.id()) : null;
            if (v == null) {
                v = byKey.get(key(in.size(), in.color()));
            }
            if (v == null) {
                v = p.addVariant(in.size(), in.color(), 0);
                created.put(v, in.stock() == null ? 0 : in.stock());
            } else {
                v.setSize(in.size());
                v.setColor(in.color());
            }
            keep.add(v);
        }
        Predicate<ProductVariant> removed = v -> keep.stream().noneMatch(k -> k == v);
        p.getVariants().removeIf(removed);
        return created;
    }

    private static String key(String size, String color) {
        return size.toLowerCase(Locale.ROOT) + "|" + color.toLowerCase(Locale.ROOT);
    }

    private Product product(Long id) {
        return products.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy sản phẩm"));
    }

    private Category category(Long id) {
        return categories.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Danh mục không tồn tại"));
    }

    private Predicate<String> slugTaken(Long excludeId) {
        return slug -> RESERVED_SLUGS.contains(slug)
                || (excludeId == null ? products.existsBySlug(slug) : products.existsBySlugAndIdNot(slug, excludeId));
    }

    private String requireFreeSlug(String slug, Long excludeId) {
        if (slugTaken(excludeId).test(slug)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Đường dẫn (slug) này đã được dùng");
        }
        return slug;
    }

    private static String uniqueSlug(String base, Predicate<String> taken) {
        String candidate = base;
        for (int i = 2; taken.test(candidate); i++) {
            candidate = base + "-" + i;
        }
        return candidate;
    }

    private static AdminProduct toAdmin(Product p) {
        return new AdminProduct(p.getId(), p.getName(), p.getSlug(), p.getDescription(), p.getPrice(),
                p.getCompareAtPrice(), p.getCategory().getId(), List.copyOf(p.getImages()),
                p.getVariants().stream()
                        .map(v -> new AdminVariantDto(v.getId(), v.getSize(), v.getColor(), v.getStock(), v.getSku()))
                        .toList(),
                p.isActive(), p.isFeatured());
    }

    // ---- Categories ----

    public CategoryDto createCategory(CategoryUpsert in) {
        String slug = in.slug() == null || in.slug().isBlank()
                ? uniqueSlug(Slugs.slugify(in.name()), categories::existsBySlug)
                : Slugs.slugify(in.slug());
        if (categories.existsBySlug(slug)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Đường dẫn (slug) này đã được dùng");
        }
        Category c = categories.save(new Category(in.name().trim(), slug, blankToNull(in.imageUrl()), in.sortOrder()));
        return new CategoryDto(c.getId(), c.getName(), c.getSlug(), c.getImageUrl(), c.getSortOrder(), 0);
    }

    public CategoryDto updateCategory(Long id, CategoryUpsert in) {
        Category c = categories.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy danh mục"));
        if (in.slug() != null && !in.slug().isBlank()) {
            String slug = Slugs.slugify(in.slug());
            if (categories.existsBySlugAndIdNot(slug, id)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Đường dẫn (slug) này đã được dùng");
            }
            c.setSlug(slug);
        }
        c.setName(in.name().trim());
        c.setImageUrl(blankToNull(in.imageUrl()));
        c.setSortOrder(in.sortOrder());
        return new CategoryDto(c.getId(), c.getName(), c.getSlug(), c.getImageUrl(), c.getSortOrder(), 0);
    }

    public void deleteCategory(Long id) {
        if (!categories.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy danh mục");
        }
        if (products.existsByCategoryId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Danh mục còn sản phẩm, hãy chuyển sản phẩm sang danh mục khác trước");
        }
        categories.deleteById(id);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
