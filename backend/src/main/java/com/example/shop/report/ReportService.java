package com.example.shop.report;

import com.example.shop.catalog.Product;
import com.example.shop.catalog.ProductRepository;
import com.example.shop.order.CustomerOrder;
import com.example.shop.order.OrderItem;
import com.example.shop.order.OrderRepository;
import com.example.shop.order.OrderStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Sales figures for the admin reports. Revenue is the goods value (unit price × quantity, without shipping) of
 * every order that was not cancelled, bucketed by day (or by month for the yearly view) in Vietnam time.
 * Aggregated in Java: the volume of a small shop fits in memory and it keeps the code database-neutral (H2/PostgreSQL).
 */
@Service
@Transactional(readOnly = true)
public class ReportService {

    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    public static final Set<Integer> PERIODS = Set.of(7, 30, 90, 365);
    private static final int TOP_PRODUCTS = 10;

    public enum Granularity {
        DAY,
        MONTH
    }

    public record Kpis(long revenue, long orders, long units, long averageOrderValue) {
    }

    /** {@code date}: the day, or the first day of the month for MONTH granularity. */
    public record Point(LocalDate date, long revenue, long orders) {
    }

    public record ProductStat(Long productId, String name, String slug, String imageUrl, long units, long revenue) {
    }

    public record CategoryStat(String name, long revenue, long units) {
    }

    /** {@code previous}: the same-length period right before, for the change shown on each KPI. */
    public record Report(int days, Granularity granularity, LocalDate from, LocalDate to, Kpis current, Kpis previous,
            List<Point> series, List<ProductStat> topProducts, List<CategoryStat> categories) {
    }

    private final OrderRepository orders;
    private final ProductRepository products;

    public ReportService(OrderRepository orders, ProductRepository products) {
        this.orders = orders;
        this.products = products;
    }

    public Report report(int days) {
        if (!PERIODS.contains(days)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Khoảng thời gian không hợp lệ");
        }
        LocalDate to = LocalDate.now(ZONE);
        LocalDate from = to.minusDays(days - 1L);
        Instant start = from.atStartOfDay(ZONE).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(ZONE).toInstant();
        Instant previousStart = from.minusDays(days).atStartOfDay(ZONE).toInstant();

        List<CustomerOrder> placed = orders.findPlacedBetween(previousStart, end, OrderStatus.CANCELLED);
        List<CustomerOrder> current = placed.stream().filter(o -> !o.getCreatedAt().isBefore(start)).toList();
        List<CustomerOrder> previous = placed.stream().filter(o -> o.getCreatedAt().isBefore(start)).toList();

        Granularity granularity = days > 90 ? Granularity.MONTH : Granularity.DAY;
        return new Report(days, granularity, from, to, kpis(current), kpis(previous),
                series(current, from, to, granularity), topProducts(current), categories(current));
    }

    private static Kpis kpis(List<CustomerOrder> list) {
        long revenue = list.stream().mapToLong(CustomerOrder::getSubtotal).sum();
        long units = list.stream().flatMap(o -> o.getItems().stream()).mapToLong(OrderItem::getQuantity).sum();
        return new Kpis(revenue, list.size(), units, list.isEmpty() ? 0 : Math.round((double) revenue / list.size()));
    }

    /** One point per day (or month), including periods without sales, so the line shows the gaps. */
    private static List<Point> series(List<CustomerOrder> list, LocalDate from, LocalDate to, Granularity granularity) {
        Function<LocalDate, LocalDate> bucket = granularity == Granularity.MONTH ? d -> d.withDayOfMonth(1) : d -> d;
        Map<LocalDate, long[]> sums = new LinkedHashMap<>();
        for (LocalDate d = bucket.apply(from); !d.isAfter(to);
                d = granularity == Granularity.MONTH ? d.plusMonths(1) : d.plusDays(1)) {
            sums.put(d, new long[2]);
        }
        for (CustomerOrder o : list) {
            long[] s = sums.get(bucket.apply(LocalDate.ofInstant(o.getCreatedAt(), ZONE)));
            if (s != null) {
                s[0] += o.getSubtotal();
                s[1]++;
            }
        }
        List<Point> points = new ArrayList<>();
        sums.forEach((date, s) -> points.add(new Point(date, s[0], s[1])));
        return points;
    }

    private static List<ProductStat> topProducts(List<CustomerOrder> list) {
        Map<Long, ProductStat> byProduct = new HashMap<>();
        // Newest orders first, so the product keeps its most recent name and image.
        list.stream().sorted(Comparator.comparing(CustomerOrder::getCreatedAt).reversed())
                .flatMap(o -> o.getItems().stream())
                .forEach(i -> byProduct.merge(i.getProductId(),
                        new ProductStat(i.getProductId(), i.getProductName(), i.getProductSlug(), i.getImageUrl(),
                                i.getQuantity(), i.getLineTotal()),
                        (a, b) -> new ProductStat(a.productId(), a.name(), a.slug(), a.imageUrl(),
                                a.units() + b.units(), a.revenue() + b.revenue())));
        return byProduct.values().stream()
                .sorted(Comparator.comparingLong(ProductStat::units).reversed()
                        .thenComparing(Comparator.comparingLong(ProductStat::revenue).reversed())
                        .thenComparing(ProductStat::name))
                .limit(TOP_PRODUCTS)
                .toList();
    }

    /** Uses each product's current category; products deleted since the sale fall under "Khác". */
    private List<CategoryStat> categories(List<CustomerOrder> list) {
        List<OrderItem> items = list.stream().flatMap(o -> o.getItems().stream()).toList();
        Map<Long, String> categoryOf = products.findAllById(items.stream().map(OrderItem::getProductId).distinct().toList())
                .stream().collect(Collectors.toMap(Product::getId, p -> p.getCategory().getName()));
        Map<String, long[]> sums = new HashMap<>();
        for (OrderItem i : items) {
            long[] s = sums.computeIfAbsent(categoryOf.getOrDefault(i.getProductId(), "Khác"), k -> new long[2]);
            s[0] += i.getLineTotal();
            s[1] += i.getQuantity();
        }
        return sums.entrySet().stream()
                .map(e -> new CategoryStat(e.getKey(), e.getValue()[0], e.getValue()[1]))
                .sorted(Comparator.comparingLong(CategoryStat::revenue).reversed().thenComparing(CategoryStat::name))
                .toList();
    }
}
