package com.example.shop.order;

import com.example.shop.common.PageResponse;
import com.example.shop.order.OrderDtos.OrderDto;
import com.example.shop.order.OrderDtos.PlaceOrderRequest;
import com.example.shop.order.OrderDtos.ShopConfig;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The logged-in customer's own orders. */
@RestController
@RequestMapping("/api")
public class OrderController {

    private final OrderService orders;

    public OrderController(OrderService orders) {
        this.orders = orders;
    }

    /** Public: shipping rules the cart page shows before checkout. */
    @GetMapping("/config")
    public ShopConfig config() {
        return OrderService.config();
    }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderDto place(Authentication auth, @Valid @RequestBody PlaceOrderRequest request) {
        return orders.place(auth.getName(), request);
    }

    @GetMapping("/orders")
    public PageResponse<OrderDto> mine(Authentication auth, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return orders.mine(auth.getName(), page, size);
    }

    @GetMapping("/orders/{id}")
    public OrderDto one(Authentication auth, @PathVariable Long id) {
        return orders.mine(auth.getName(), id);
    }

    @PostMapping("/orders/{id}/cancel")
    public OrderDto cancel(Authentication auth, @PathVariable Long id) {
        return orders.cancel(auth.getName(), id);
    }
}
