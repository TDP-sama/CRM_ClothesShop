package com.example.shop.review;

import com.example.shop.auth.AppUser;
import com.example.shop.auth.UserService;
import com.example.shop.catalog.Product;
import com.example.shop.catalog.ProductRepository;
import com.example.shop.common.PageResponse;
import com.example.shop.order.OrderRepository;
import com.example.shop.order.OrderStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Product reviews. Reading is public; only customers who received the product may write one, once. */
@RestController
@RequestMapping("/api/products/{slug}/reviews")
public class ReviewController {

    record ReviewRequest(
            @NotNull(message = "Vui lòng chọn số sao") @Min(value = 1, message = "Tối thiểu 1 sao")
            @Max(value = 5, message = "Tối đa 5 sao") Integer rating,
            @Size(max = 1000, message = "Nhận xét tối đa 1000 ký tự") String comment) {
    }

    record ReviewDto(Long id, String authorName, int rating, String comment, Instant createdAt) {
        static ReviewDto of(Review r) {
            return new ReviewDto(r.getId(), r.getUser().getDisplayName(), r.getRating(), r.getComment(),
                    r.getCreatedAt());
        }
    }

    /** {@code reason}: LOGIN_REQUIRED, NOT_PURCHASED, ALREADY_REVIEWED, or null when {@code canReview}. */
    record Eligibility(boolean canReview, String reason) {
    }

    private final ReviewRepository reviews;
    private final ProductRepository products;
    private final OrderRepository orders;
    private final UserService users;

    public ReviewController(ReviewRepository reviews, ProductRepository products, OrderRepository orders,
            UserService users) {
        this.reviews = reviews;
        this.products = products;
        this.orders = orders;
        this.users = users;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public PageResponse<ReviewDto> list(@PathVariable String slug, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Product product = product(slug);
        return PageResponse.of(reviews.findByProductIdOrderByCreatedAtDesc(product.getId(),
                PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 50))), ReviewDto::of);
    }

    @GetMapping("/eligibility")
    @Transactional(readOnly = true)
    public Eligibility eligibility(@PathVariable String slug, Authentication auth) {
        Product product = product(slug);
        if (auth == null || auth instanceof AnonymousAuthenticationToken) {
            return new Eligibility(false, "LOGIN_REQUIRED");
        }
        String reason = ineligibility(product, users.require(auth.getName()));
        return new Eligibility(reason == null, reason);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public ReviewDto create(@PathVariable String slug, Authentication auth, @Valid @RequestBody ReviewRequest request) {
        Product product = product(slug);
        AppUser user = users.require(auth.getName());
        String reason = ineligibility(product, user);
        if ("NOT_PURCHASED".equals(reason)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Bạn chỉ có thể đánh giá sản phẩm đã mua và nhận hàng");
        }
        if ("ALREADY_REVIEWED".equals(reason)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bạn đã đánh giá sản phẩm này");
        }
        String comment = request.comment() == null || request.comment().isBlank() ? null : request.comment().trim();
        try {
            return ReviewDto.of(reviews.saveAndFlush(new Review(product, user, request.rating(), comment)));
        } catch (DataIntegrityViolationException e) {
            // A double-submitted form: the unique (product, user) constraint already holds the first review.
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bạn đã đánh giá sản phẩm này");
        }
    }

    private String ineligibility(Product product, AppUser user) {
        if (!orders.hasPurchased(user.getId(), product.getId(), OrderStatus.DELIVERED)) {
            return "NOT_PURCHASED";
        }
        if (reviews.existsByProductIdAndUserId(product.getId(), user.getId())) {
            return "ALREADY_REVIEWED";
        }
        return null;
    }

    private Product product(String slug) {
        return products.findBySlugAndActiveTrue(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy sản phẩm"));
    }
}
