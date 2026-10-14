package com.example.shop.order;

import com.example.shop.auth.AppUser;
import com.example.shop.auth.UserService;
import com.example.shop.catalog.Product;
import com.example.shop.catalog.ProductVariant;
import com.example.shop.catalog.ProductVariantRepository;
import com.example.shop.common.PageResponse;
import com.example.shop.inventory.InventoryService;
import com.example.shop.inventory.MovementType;
import com.example.shop.order.OrderDtos.CartItem;
import com.example.shop.order.OrderDtos.OrderDto;
import com.example.shop.order.OrderDtos.OrderItemDto;
import com.example.shop.order.OrderDtos.PlaceOrderRequest;
import com.example.shop.order.OrderDtos.ShopConfig;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Checkout (cash on delivery), order history and the order status workflow. */
@Service
@Transactional
public class OrderService {

    public static final long SHIPPING_FEE = 30_000;
    public static final long FREE_SHIPPING_THRESHOLD = 500_000;

    private final OrderRepository orders;
    private final ProductVariantRepository variants;
    private final UserService users;
    private final InventoryService inventory;

    public OrderService(OrderRepository orders, ProductVariantRepository variants, UserService users,
            InventoryService inventory) {
        this.orders = orders;
        this.variants = variants;
        this.users = users;
        this.inventory = inventory;
    }

    public static ShopConfig config() {
        return new ShopConfig(SHIPPING_FEE, FREE_SHIPPING_THRESHOLD);
    }

    /**
     * Prices and stock come from the database, never from the client. The variant rows are locked for the
     * transaction, so concurrent checkouts cannot oversell.
     */
    public OrderDto place(String email, PlaceOrderRequest request) {
        AppUser user = users.require(email);
        Map<Long, Integer> quantities = request.items().stream()
                .collect(Collectors.toMap(CartItem::variantId, CartItem::quantity, Integer::sum, LinkedHashMap::new));
        Map<Long, ProductVariant> locked = variants.findAllForUpdate(quantities.keySet()).stream()
                .collect(Collectors.toMap(ProductVariant::getId, Function.identity()));
        if (locked.size() != quantities.size()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Một số sản phẩm trong giỏ không còn bán, vui lòng cập nhật giỏ hàng");
        }

        CustomerOrder order = new CustomerOrder(user, request.recipientName(), request.phone(), request.address(),
                request.note());
        long subtotal = 0;
        for (Map.Entry<Long, Integer> line : quantities.entrySet()) {
            ProductVariant variant = locked.get(line.getKey());
            Product product = variant.getProduct();
            int quantity = line.getValue();
            if (!product.isActive()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Sản phẩm \"%s\" đã ngừng bán"
                        .formatted(product.getName()));
            }
            if (variant.getStock() < quantity) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "\"%s\" (size %s, màu %s) chỉ còn %d sản phẩm".formatted(product.getName(),
                                variant.getSize(), variant.getColor(), variant.getStock()));
            }
            order.addItem(new OrderItem(product.getId(), variant.getId(), product.getName(), product.getSlug(),
                    product.getImages().isEmpty() ? null : product.getImages().get(0),
                    variant.getSize(), variant.getColor(), product.getPrice(), quantity));
            subtotal += product.getPrice() * quantity;
        }
        order.setTotals(subtotal, subtotal >= FREE_SHIPPING_THRESHOLD ? 0 : SHIPPING_FEE);
        CustomerOrder saved = orders.save(order);
        // Taken after saving so the stock ledger can reference the order number.
        quantities.forEach((variantId, quantity) -> inventory.change(locked.get(variantId), -quantity,
                MovementType.SALE, null, null, saved.getCode(), user.getEmail()));
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderDto> mine(String email, int page, int size) {
        AppUser user = users.require(email);
        return PageResponse.of(orders.findByUserId(user.getId(), PageRequest.of(Math.max(page, 0),
                Math.clamp(size, 1, 50), Sort.by("createdAt").descending())), this::toDto);
    }

    @Transactional(readOnly = true)
    public OrderDto mine(String email, Long id) {
        return toDto(owned(email, id));
    }

    /** Customers may cancel only while the shop has not confirmed the order yet. */
    public OrderDto cancel(String email, Long id) {
        CustomerOrder order = owned(email, id);
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Đơn hàng đã được xác nhận, vui lòng liên hệ cửa hàng để hủy");
        }
        changeStatus(order, OrderStatus.CANCELLED, email);
        return toDto(order);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderDto> all(OrderStatus status, int page, int size) {
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100),
                Sort.by("createdAt").descending());
        return PageResponse.of(status == null ? orders.findAllBy(pageRequest) : orders.findByStatus(status, pageRequest),
                this::toDto);
    }

    @Transactional(readOnly = true)
    public OrderDto get(Long id) {
        return toDto(order(id));
    }

    public OrderDto updateStatus(Long id, OrderStatus status, String actorEmail) {
        CustomerOrder order = order(id);
        if (!order.getStatus().next().contains(status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Không thể chuyển đơn từ %s sang %s".formatted(order.getStatus(), status));
        }
        changeStatus(order, status, actorEmail);
        return toDto(order);
    }

    private void changeStatus(CustomerOrder order, OrderStatus status, String actorEmail) {
        if (status == OrderStatus.CANCELLED) {
            restock(order, actorEmail);
        }
        order.setStatus(status);
    }

    /** Puts cancelled items back on the shelf; variants deleted since the purchase are skipped. */
    private void restock(CustomerOrder order, String actorEmail) {
        Map<Long, Integer> quantities = order.getItems().stream()
                .collect(Collectors.toMap(OrderItem::getVariantId, OrderItem::getQuantity, Integer::sum));
        variants.findAllForUpdate(quantities.keySet()).forEach(v -> inventory.change(v, quantities.get(v.getId()),
                MovementType.RETURN, null, "Đơn hàng bị hủy", order.getCode(), actorEmail));
    }

    private CustomerOrder owned(String email, Long id) {
        AppUser user = users.require(email);
        // 404 rather than 403 for other people's orders: do not reveal which order numbers exist.
        return orders.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng"));
    }

    private CustomerOrder order(Long id) {
        return orders.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng"));
    }

    private OrderDto toDto(CustomerOrder o) {
        List<OrderItemDto> items = o.getItems().stream()
                .map(i -> new OrderItemDto(i.getProductId(), i.getProductSlug(), i.getProductName(), i.getImageUrl(),
                        i.getSize(), i.getColor(), i.getUnitPrice(), i.getQuantity(), i.getLineTotal()))
                .toList();
        return new OrderDto(o.getId(), o.getCode(), o.getStatus(),
                o.getStatus().next().stream().sorted(Comparator.naturalOrder()).toList(),
                o.getStatus() == OrderStatus.PENDING, o.getUser().getEmail(), o.getRecipientName(), o.getPhone(),
                o.getAddress(), o.getNote(), "COD", o.getSubtotal(), o.getShippingFee(), o.getTotal(),
                items.stream().mapToInt(OrderItemDto::quantity).sum(), items, o.getCreatedAt(), o.getUpdatedAt());
    }
}
