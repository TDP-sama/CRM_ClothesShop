package com.example.shop.catalog;

import com.example.shop.catalog.CatalogDtos.CategoryDto;
import com.example.shop.catalog.CatalogDtos.Filters;
import com.example.shop.catalog.CatalogDtos.ProductDetail;
import com.example.shop.catalog.CatalogDtos.ProductSummary;
import com.example.shop.catalog.CatalogService.ProductQuery;
import com.example.shop.common.PageResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public storefront API: no login needed. */
@RestController
@RequestMapping("/api")
public class CatalogController {

    private final CatalogService catalog;

    public CatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/categories")
    public List<CategoryDto> categories() {
        return catalog.categories();
    }

    /**
     * @param sort {@code newest} (default), {@code price-asc}, {@code price-desc} or {@code name}
     * @param size page size, at most {@value CatalogService#MAX_PAGE_SIZE}
     */
    @GetMapping("/products")
    public PageResponse<ProductSummary> products(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String q,
            @RequestParam(name = "sizes", required = false) List<String> sizes,
            @RequestParam(name = "colors", required = false) List<String> colors,
            @RequestParam(required = false) Long minPrice,
            @RequestParam(required = false) Long maxPrice,
            @RequestParam(defaultValue = "false") boolean sale,
            @RequestParam(defaultValue = "false") boolean featured,
            @RequestParam(defaultValue = "newest") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        return catalog.search(new ProductQuery(category, q, sizes == null ? List.of() : sizes,
                colors == null ? List.of() : colors, minPrice, maxPrice, sale, featured, sort, page, size));
    }

    @GetMapping("/products/filters")
    public Filters filters() {
        return catalog.filters();
    }

    @GetMapping("/products/{slug}")
    public ProductDetail product(@PathVariable String slug) {
        return catalog.detail(slug);
    }

    @GetMapping("/products/{slug}/related")
    public List<ProductSummary> related(@PathVariable String slug, @RequestParam(defaultValue = "4") int limit) {
        return catalog.related(slug, limit);
    }
}
