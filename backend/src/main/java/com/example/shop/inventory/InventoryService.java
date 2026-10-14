package com.example.shop.inventory;

import com.example.shop.catalog.Product;
import com.example.shop.catalog.ProductVariant;
import com.example.shop.catalog.ProductVariantRepository;
import com.example.shop.common.PageResponse;
import com.example.shop.common.Slugs;
import com.example.shop.inventory.InventoryDtos.AdjustmentRequest;
import com.example.shop.inventory.InventoryDtos.InventoryItem;
import com.example.shop.inventory.InventoryDtos.MovementDto;
import com.example.shop.inventory.InventoryDtos.ReceiptDto;
import com.example.shop.inventory.InventoryDtos.ReceiptLineRequest;
import com.example.shop.inventory.InventoryDtos.ReceiptRequest;
import com.example.shop.inventory.InventoryDtos.SettingsUpdate;
import com.example.shop.inventory.InventoryDtos.StockStatus;
import com.example.shop.inventory.InventoryDtos.Summary;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * The single place where variant stock changes. Every change is written to the stock ledger
 * ({@link StockMovement}) with its type, the stock before/after, the related document and who caused it.
 */
@Service
@Transactional
@Order(10) // after DemoDataSeeder, so freshly seeded variants get SKUs and opening balances too
public class InventoryService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);
    private static final int SKU_MAX = 64;

    private final ProductVariantRepository variants;
    private final StockMovementRepository movements;
    private final GoodsReceiptRepository receipts;

    public InventoryService(ProductVariantRepository variants, StockMovementRepository movements,
            GoodsReceiptRepository receipts) {
        this.variants = variants;
        this.movements = movements;
        this.receipts = receipts;
    }

    // ---- Stock changes (callers must hold the variant row lock, see ProductVariantRepository.findAllForUpdate) ----

    /** Adds (positive) or removes (negative) stock and records why. */
    public StockMovement change(ProductVariant variant, int delta, MovementType type, String reason, String note,
            String referenceCode, String actorEmail) {
        int before = variant.getStock();
        int after = before + delta;
        if (after < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tồn kho không đủ");
        }
        variant.setStock(after);
        return movements.save(new StockMovement(variant.getId(), variant.getProduct().getId(), variant.getProduct().getName(),
                variant.getSku(), variant.getSize(), variant.getColor(), type, before, after, reason, note,
                referenceCode, actorEmail));
    }

    /** Opening balance of a variant just created by an admin; also gives it a SKU. */
    public void open(ProductVariant variant, int initialStock, String actorEmail) {
        if (variant.getSku() == null) {
            variant.setSku(uniqueSku(variant.getProduct(), variant.getSize(), variant.getColor(),
                    new HashSet<>(variants.findAllSkus())));
        }
        variant.setStock(0);
        change(variant, initialStock, MovementType.INITIAL, null, "Tồn ban đầu khi tạo biến thể", null, actorEmail);
    }

    public ReceiptDto receive(ReceiptRequest request, String actorEmail) {
        Map<Long, Integer> quantities = request.lines().stream().collect(Collectors.toMap(
                ReceiptLineRequest::variantId, ReceiptLineRequest::quantity, Integer::sum, LinkedHashMap::new));
        Map<Long, ProductVariant> locked = lock(quantities.keySet());

        GoodsReceipt receipt = receipts.save(new GoodsReceipt(blankToNull(request.supplier()),
                blankToNull(request.note()), actorEmail));
        quantities.forEach((variantId, quantity) -> {
            ProductVariant v = locked.get(variantId);
            receipt.addLine(v.getId(), v.getProduct().getName(), v.getSku(), v.getSize(), v.getColor(), quantity);
            change(v, quantity, MovementType.RECEIPT, null, receipt.getSupplier(), receipt.getCode(), actorEmail);
        });
        return ReceiptDto.of(receipt);
    }

    /** Sets the stock to what was actually counted and records the difference. */
    public MovementDto adjust(AdjustmentRequest request, String actorEmail) {
        ProductVariant v = lock(Set.of(request.variantId())).get(request.variantId());
        int delta = request.actualQuantity() - v.getStock();
        if (delta == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Số lượng thực tế bằng tồn hiện tại, không cần điều chỉnh");
        }
        return MovementDto.of(change(v, delta, MovementType.ADJUSTMENT, request.reason().name(),
                blankToNull(request.note()), null, actorEmail));
    }

    public InventoryItem updateSettings(Long variantId, SettingsUpdate request) {
        ProductVariant v = variants.findById(variantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy biến thể"));
        if (request.sku() != null && !request.sku().isBlank()) {
            String sku = normalizeSku(request.sku());
            if (variants.existsBySkuAndIdNot(sku, variantId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Mã SKU này đã được dùng cho sản phẩm khác");
            }
            v.setSku(sku);
        }
        v.setLowStockThreshold(request.lowStockThreshold());
        return toItem(v);
    }

    private Map<Long, ProductVariant> lock(Set<Long> ids) {
        Map<Long, ProductVariant> locked = variants.findAllForUpdate(ids).stream()
                .collect(Collectors.toMap(ProductVariant::getId, Function.identity()));
        if (locked.size() != ids.size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy một số biến thể sản phẩm");
        }
        return locked;
    }

    // ---- Queries ----

    @Transactional(readOnly = true)
    public PageResponse<InventoryItem> items(String q, String category, StockStatus status, int page, int size) {
        Specification<ProductVariant> spec = (root, query, cb) -> {
            Join<ProductVariant, Product> product = root.join("product");
            List<Predicate> where = new ArrayList<>();
            if (q != null && !q.isBlank()) {
                String text = "%" + q.trim().toLowerCase(Locale.ROOT).replace("%", "").replace("_", "") + "%";
                String slug = "%" + Slugs.slugify(q) + "%";
                where.add(cb.or(cb.like(cb.lower(product.get("name")), text), cb.like(product.get("slug"), slug),
                        cb.like(cb.lower(root.get("sku")), text)));
            }
            if (category != null && !category.isBlank()) {
                where.add(cb.equal(product.get("category").get("slug"), category));
            }
            if (status != null) {
                where.add(switch (status) {
                    case OUT -> cb.equal(root.get("stock"), 0);
                    case LOW -> cb.and(cb.greaterThan(root.get("stock"), 0),
                            cb.lessThanOrEqualTo(root.get("stock"), root.<Integer>get("lowStockThreshold")));
                    case IN_STOCK -> cb.greaterThan(root.get("stock"), root.<Integer>get("lowStockThreshold"));
                });
                where.add(cb.isTrue(product.get("active"))); // hidden products are not on sale, so no alerts
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100),
                Sort.by("product.name").ascending().and(Sort.by("id").ascending()));
        return PageResponse.of(variants.findAll(spec, pageRequest), InventoryService::toItem);
    }

    @Transactional(readOnly = true)
    public Summary summary() {
        return new Summary(variants.sumActiveStock(), variants.countActive(), variants.countActiveLowStock(),
                variants.countActiveOutOfStock());
    }

    @Transactional(readOnly = true)
    public PageResponse<MovementDto> movements(Long variantId, MovementType type, int page, int size) {
        Specification<StockMovement> spec = (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            if (variantId != null) {
                where.add(cb.equal(root.get("variantId"), variantId));
            }
            if (type != null) {
                where.add(cb.equal(root.get("type"), type));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
        return PageResponse.of(movements.findAll(spec, PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100),
                newestFirst())), MovementDto::of);
    }

    @Transactional(readOnly = true)
    public PageResponse<ReceiptDto> receipts(int page, int size) {
        return PageResponse.of(receipts.findAll(PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100),
                newestFirst())), ReceiptDto::of);
    }

    @Transactional(readOnly = true)
    public ReceiptDto receipt(Long id) {
        return receipts.findById(id).map(ReceiptDto::of)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy phiếu nhập"));
    }

    static InventoryItem toItem(ProductVariant v) {
        Product p = v.getProduct();
        StockStatus status = v.getStock() == 0 ? StockStatus.OUT
                : v.getStock() <= v.getLowStockThreshold() ? StockStatus.LOW : StockStatus.IN_STOCK;
        return new InventoryItem(v.getId(), p.getId(), p.getName(), p.getSlug(), p.isActive(),
                p.getImages().isEmpty() ? null : p.getImages().get(0), p.getCategory().getName(), v.getSku(),
                v.getSize(), v.getColor(), v.getStock(), v.getLowStockThreshold(), status);
    }

    // ---- SKUs and the opening balance of pre-existing data ----

    /**
     * Gives variants created before inventory tracking a SKU and records their current stock as the opening
     * balance, so the ledger always explains the current stock. Does nothing once everything is tracked.
     */
    @Override
    public void run(ApplicationArguments args) {
        List<ProductVariant> all = variants.findAll();
        if (all.isEmpty()) {
            return;
        }
        Set<String> taken = new HashSet<>(variants.findAllSkus());
        Set<Long> tracked = new HashSet<>(movements.findTrackedVariantIds(all.stream().map(ProductVariant::getId).toList()));
        int skus = 0;
        int opened = 0;
        for (ProductVariant v : all) {
            if (v.getSku() == null) {
                v.setSku(uniqueSku(v.getProduct(), v.getSize(), v.getColor(), taken));
                skus++;
            }
            if (!tracked.contains(v.getId())) {
                int stock = v.getStock();
                v.setStock(0);
                change(v, stock, MovementType.INITIAL, null, "Tồn đầu kỳ khi bắt đầu quản lý kho", null, null);
                opened++;
            }
        }
        if (skus > 0 || opened > 0) {
            log.info("Inventory backfill: {} SKUs generated, {} opening balances recorded", skus, opened);
        }
    }

    /** e.g. "AO-THUN-COTTON-BASIC-TRANG-M"; adds -2, -3... on collision. Reserves the result in {@code taken}. */
    static String uniqueSku(Product product, String size, String color, Set<String> taken) {
        String suffix = "-" + Slugs.slugify(color) + "-" + Slugs.slugify(size);
        String base = Slugs.slugify(product.getName());
        base = base.substring(0, Math.min(base.length(), Math.max(8, SKU_MAX - suffix.length() - 3)));
        String candidate = normalizeSku(base + suffix);
        for (int i = 2; taken.contains(candidate); i++) {
            candidate = normalizeSku(base + suffix + "-" + i);
        }
        taken.add(candidate);
        return candidate;
    }

    private static String normalizeSku(String sku) {
        String s = sku.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", "-");
        return s.length() > SKU_MAX ? s.substring(0, SKU_MAX) : s;
    }

    private static Sort newestFirst() {
        return Sort.by("createdAt").descending().and(Sort.by("id").descending());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
