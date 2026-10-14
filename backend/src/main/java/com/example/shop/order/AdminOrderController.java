package com.example.shop.order;

import com.example.shop.auth.AppUserRepository;
import com.example.shop.catalog.ProductRepository;
import com.example.shop.catalog.ProductVariantRepository;
import org.springframework.security.core.Authentication;
import com.example.shop.common.PageResponse;
import com.example.shop.order.OrderDtos.OrderDto;
import com.example.shop.order.OrderDtos.Stats;
import com.example.shop.order.OrderDtos.StatusUpdate;
import jakarta.validation.Valid;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Order management and dashboard numbers; {@code /api/admin/**} requires the ADMIN role. */
@RestController
@RequestMapping("/api/admin")
public class AdminOrderController {

    private final OrderService orders;
    private final OrderRepository orderRepository;
    private final ProductRepository products;
    private final ProductVariantRepository variants;
    private final AppUserRepository users;

    public AdminOrderController(OrderService orders, OrderRepository orderRepository, ProductRepository products,
            ProductVariantRepository variants, AppUserRepository users) {
        this.orders = orders;
        this.orderRepository = orderRepository;
        this.products = products;
        this.variants = variants;
        this.users = users;
    }

    @GetMapping("/orders")
    public PageResponse<OrderDto> list(@RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return orders.all(status, page, size);
    }

    @GetMapping("/orders/{id}")
    public OrderDto one(@PathVariable Long id) {
        return orders.get(id);
    }

    @PatchMapping("/orders/{id}/status")
    public OrderDto updateStatus(Authentication auth, @PathVariable Long id, @Valid @RequestBody StatusUpdate request) {
        return orders.updateStatus(id, request.status(), auth.getName());
    }

    @GetMapping("/stats")
    @Transactional(readOnly = true)
    public Stats stats() {
        return new Stats(products.countByActiveTrue(), users.count(), orderRepository.count(),
                orderRepository.countByStatus(OrderStatus.PENDING),
                orderRepository.sumTotalByStatus(OrderStatus.DELIVERED),
                variants.countActiveLowStock() + variants.countActiveOutOfStock());
    }
}
