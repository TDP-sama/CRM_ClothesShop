package com.example.shop.catalog;

import com.example.shop.catalog.CatalogDtos.CategoryDto;
import com.example.shop.catalog.CatalogDtos.CategoryRef;
import com.example.shop.catalog.CatalogDtos.Filters;
import com.example.shop.catalog.CatalogDtos.ProductDetail;
import com.example.shop.catalog.CatalogDtos.ProductSummary;
import com.example.shop.catalog.CatalogDtos.VariantDto;
import com.example.shop.common.PageResponse;
import com.example.shop.common.Slugs;
import com.example.shop.review.ReviewRepository;
import com.example.shop.review.ReviewRepository.RatingRow;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Read-only storefront queries. Only active products are ever returned. */
@Service
@Transactional(readOnly = true)
public class CatalogService {

    public static final int MAX_PAGE_SIZE = 48;

    /** Search and filter parameters of {@code GET /api/products}. Empty lists and nulls mean "no filter". */
    public record ProductQuery(String category, String q, List<String> sizes, List<String> colors,
            Long minPrice, Long maxPrice, boolean sale, boolean featured, String sort, int page, int size) {
    }

    private final CategoryRepository categories;
    private final ProductRepository products;
    private final ProductVariantRepository variants;
    private final ReviewRepository reviews;

    public CatalogService(CategoryRepository categories, ProductRepository products,
            ProductVariantRepository variants, ReviewRepository reviews) {
        this.categories = categories;
        this.products = products;
        this.variants = variants;
        this.reviews = reviews;
    }

    public List<CategoryDto> categories() {
        Map<Long, Long> counts = products.countActiveByCategory().stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
        return categories.findAllByOrderBySortOrderAscNameAsc().stream()
                .map(c -> new CategoryDto(c.getId(), c.getName(), c.getSlug(), c.getImageUrl(), c.getSortOrder(),
                        counts.getOrDefault(c.getId(), 0L)))
                .toList();
    }

    public Filters filters() {
        return new Filters(Sizes.sorted(variants.findActiveSizes()), variants.findActiveColors());
    }

    public PageResponse<ProductSummary> search(ProductQuery query) {
        PageRequest pageRequest = PageRequest.of(Math.max(query.page(), 0),
                Math.clamp(query.size(), 1, MAX_PAGE_SIZE), sortFor(query.sort()));
        Page<Product> page = products.findAll(specificationFor(query), pageRequest);
        Function<Product, ProductSummary> mapper = summaryMapper(page.getContent());
        return PageResponse.of(page, mapper);
    }

    public ProductDetail detail(String slug) {
        Product p = findVisible(slug);
        RatingRow rating = ratings(List.of(p.getId())).get(p.getId());
        List<ProductVariant> sorted = p.getVariants().stream()
                .sorted((a, b) -> Sizes.ORDER.compare(a.getSize(), b.getSize()))
                .toList();
        return new ProductDetail(p.getId(), p.getSlug(), p.getName(), p.getDescription(), p.getPrice(),
                p.getCompareAtPrice(), List.copyOf(p.getImages()),
                new CategoryRef(p.getCategory().getName(), p.getCategory().getSlug()),
                Sizes.sorted(sorted.stream().map(ProductVariant::getSize).toList()),
                colorsOf(p),
                sorted.stream().map(v -> new VariantDto(v.getId(), v.getSize(), v.getColor(), v.getStock())).toList(),
                rating == null ? 0 : rating.getAverage(), rating == null ? 0 : rating.getCount());
    }

    public List<ProductSummary> related(String slug, int limit) {
        Product p = findVisible(slug);
        List<Product> related = products.findRelated(p.getCategory().getId(), p.getId(),
                PageRequest.of(0, Math.clamp(limit, 1, 12)));
        return related.stream().map(summaryMapper(related)).toList();
    }

    Product findVisible(String slug) {
        return products.findBySlugAndActiveTrue(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy sản phẩm"));
    }

    /** Builds summaries for a batch of products with one rating query for the whole batch. */
    private Function<Product, ProductSummary> summaryMapper(Collection<Product> batch) {
        Map<Long, RatingRow> ratings = ratings(batch.stream().map(Product::getId).toList());
        return p -> {
            RatingRow rating = ratings.get(p.getId());
            List<String> images = p.getImages();
            return new ProductSummary(p.getId(), p.getSlug(), p.getName(), p.getPrice(), p.getCompareAtPrice(),
                    images.isEmpty() ? null : images.get(0), images.size() > 1 ? images.get(1) : null,
                    p.getCategory().getName(), colorsOf(p),
                    Sizes.sorted(p.getVariants().stream().map(ProductVariant::getSize).toList()),
                    p.getVariants().stream().anyMatch(v -> v.getStock() > 0),
                    rating == null ? 0 : rating.getAverage(), rating == null ? 0 : rating.getCount(),
                    p.getCreatedAt());
        };
    }

    private Map<Long, RatingRow> ratings(Collection<Long> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, RatingRow> byProduct = new HashMap<>();
        reviews.ratingsFor(productIds).forEach(row -> byProduct.put(row.getProductId(), row));
        return byProduct;
    }

    private static List<String> colorsOf(Product p) {
        return List.copyOf(p.getVariants().stream().map(ProductVariant::getColor)
                .collect(Collectors.toCollection(LinkedHashSet::new)));
    }

    private static Sort sortFor(String sort) {
        Sort base = switch (sort == null ? "" : sort) {
            case "price-asc" -> Sort.by("price").ascending();
            case "price-desc" -> Sort.by("price").descending();
            case "name" -> Sort.by("name").ascending();
            default -> Sort.by("createdAt").descending();
        };
        return base.and(Sort.by("id").descending());
    }

    private static Specification<Product> specificationFor(ProductQuery query) {
        return (root, criteria, cb) -> {
            List<Predicate> where = new ArrayList<>();
            where.add(cb.isTrue(root.get("active")));
            if (notBlank(query.category())) {
                where.add(cb.equal(root.get("category").get("slug"), query.category()));
            }
            if (notBlank(query.q())) {
                // Slugs are accent-free, so "ao khoac" finds "Áo khoác"; the name match keeps exact-accent queries.
                String slugLike = "%" + Slugs.slugify(query.q()) + "%";
                String nameLike = "%" + query.q().trim().toLowerCase(Locale.ROOT).replace("%", "").replace("_", "") + "%";
                where.add(cb.or(
                        cb.like(root.get("slug"), slugLike),
                        cb.like(cb.lower(root.get("name")), nameLike),
                        cb.like(root.get("category").get("slug"), slugLike)));
            }
            if (!query.sizes().isEmpty() || !query.colors().isEmpty()) {
                where.add(cb.exists(availableVariant(root, criteria, cb, query)));
            }
            if (query.minPrice() != null) {
                where.add(cb.greaterThanOrEqualTo(root.get("price"), query.minPrice()));
            }
            if (query.maxPrice() != null) {
                where.add(cb.lessThanOrEqualTo(root.get("price"), query.maxPrice()));
            }
            if (query.sale()) {
                where.add(cb.greaterThan(root.get("compareAtPrice"), root.<Long>get("price")));
            }
            if (query.featured()) {
                where.add(cb.isTrue(root.get("featured")));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
    }

    /** A variant of this product in stock that matches the chosen sizes and colors. */
    private static Subquery<Long> availableVariant(Root<Product> product, CriteriaQuery<?> criteria,
            CriteriaBuilder cb, ProductQuery query) {
        Subquery<Long> sub = criteria.subquery(Long.class);
        Root<ProductVariant> v = sub.from(ProductVariant.class);
        List<Predicate> match = new ArrayList<>();
        match.add(cb.equal(v.get("product"), product));
        match.add(cb.greaterThan(v.get("stock"), 0));
        if (!query.sizes().isEmpty()) {
            match.add(v.get("size").in(query.sizes()));
        }
        if (!query.colors().isEmpty()) {
            match.add(v.get("color").in(query.colors()));
        }
        return sub.select(v.get("id")).where(match.toArray(Predicate[]::new));
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
