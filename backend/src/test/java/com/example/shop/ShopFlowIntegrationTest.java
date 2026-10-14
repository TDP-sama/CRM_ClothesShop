package com.example.shop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.shop.auth.AppUser;
import com.example.shop.auth.AppUserRepository;
import com.example.shop.auth.Role;
import com.example.shop.catalog.Category;
import com.example.shop.catalog.CategoryRepository;
import com.example.shop.catalog.Product;
import com.example.shop.catalog.ProductRepository;
import com.example.shop.catalog.ProductVariantRepository;
import com.example.shop.order.OrderRepository;
import com.example.shop.review.ReviewRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Storefront, checkout, reviews and admin flows against H2, authenticated with mock users. */
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:shopflow;DB_CLOSE_DELAY=-1", "app.seed-demo-data=false"})
@AutoConfigureMockMvc
class ShopFlowIntegrationTest {

    private static final RequestPostProcessor CUSTOMER = user("customer@example.com").roles("CUSTOMER");
    private static final RequestPostProcessor OTHER = user("other@example.com").roles("CUSTOMER");
    private static final RequestPostProcessor ADMIN = user("admin@example.com").roles("ADMIN");

    @Autowired private MockMvc mvc;
    @Autowired private AppUserRepository users;
    @Autowired private CategoryRepository categories;
    @Autowired private ProductRepository products;
    @Autowired private ProductVariantRepository variants;
    @Autowired private OrderRepository orders;
    @Autowired private ReviewRepository reviews;

    private Long categoryId;
    private Long mediumId;
    private Long largeId;

    @BeforeEach
    void setUp() {
        reviews.deleteAll();
        orders.deleteAll();
        products.deleteAll();
        categories.deleteAll();
        users.deleteAll();
        users.save(new AppUser("customer@example.com", "{noop}x", "Khách Hàng", Role.CUSTOMER));
        users.save(new AppUser("other@example.com", "{noop}x", "Người Khác", Role.CUSTOMER));
        users.save(new AppUser("admin@example.com", "{noop}x", "Quản Trị", Role.ADMIN));

        Category tops = categories.save(new Category("Áo", "ao", null, 1));
        categories.save(new Category("Quần", "quan", null, 2));
        categoryId = tops.getId();
        Product tee = new Product("Áo thun test", "ao-thun-test", tops);
        tee.setPrice(199_000);
        tee.getImages().add("https://example.com/tee.jpg");
        tee.addVariant("L", "Đen", 0);
        tee.addVariant("M", "Đen", 2);
        products.save(tee);
        mediumId = tee.getVariants().get(1).getId();
        largeId = tee.getVariants().get(0).getId();
    }

    // ---- Storefront ----

    @Test
    void catalogIsPublicSearchableAndFilterable() throws Exception {
        mvc.perform(get("/api/products")).andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(1)));
        mvc.perform(get("/api/products").param("category", "quan")).andExpect(jsonPath("$.items", hasSize(0)));
        // Accent-insensitive search.
        mvc.perform(get("/api/products").param("q", "ao thun")).andExpect(jsonPath("$.items", hasSize(1)));
        mvc.perform(get("/api/products").param("q", "Áo thun")).andExpect(jsonPath("$.items", hasSize(1)));
        // Size filters only match variants in stock.
        mvc.perform(get("/api/products").param("sizes", "L")).andExpect(jsonPath("$.items", hasSize(0)));
        mvc.perform(get("/api/products").param("sizes", "M")).andExpect(jsonPath("$.items", hasSize(1)));

        mvc.perform(get("/api/products/ao-thun-test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sizes", contains("M", "L")))
                .andExpect(jsonPath("$.variants", hasSize(2)))
                .andExpect(jsonPath("$.category.slug").value("ao"));
        mvc.perform(get("/api/categories")).andExpect(jsonPath("$[0].productCount").value(1));
        mvc.perform(get("/api/products/khong-ton-tai")).andExpect(status().isNotFound());
    }

    // ---- Checkout ----

    @Test
    void placingAnOrderUsesServerPricesAndTakesStock() throws Exception {
        placeOrder(CUSTOMER, mediumId, 2)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.subtotal").value(398_000))
                .andExpect(jsonPath("$.shippingFee").value(30_000))
                .andExpect(jsonPath("$.total").value(428_000))
                .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.startsWith("DH")));
        assertThat(variants.findById(mediumId).orElseThrow().getStock()).isZero();

        placeOrder(CUSTOMER, mediumId, 1).andExpect(status().isConflict());
        placeOrder(CUSTOMER, largeId, 1).andExpect(status().isConflict());
    }

    @Test
    void checkoutRequiresLoginAndValidShippingDetails() throws Exception {
        mvc.perform(post("/api/orders").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(order(mediumId, 1)))
                .andExpect(status().isUnauthorized());
        mvc.perform(json(post("/api/orders"), CUSTOMER, """
                        {"items":[{"variantId":%d,"quantity":1}],"recipientName":"A","phone":"123","address":"HN"}
                        """.formatted(mediumId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.phone").value("Số điện thoại không hợp lệ"));
    }

    @Test
    void customersSeeOnlyTheirOwnOrdersAndCanCancelPendingOnes() throws Exception {
        long id = orderId(placeOrder(CUSTOMER, mediumId, 2));

        mvc.perform(get("/api/orders/" + id).with(OTHER)).andExpect(status().isNotFound());
        mvc.perform(get("/api/orders").with(OTHER)).andExpect(jsonPath("$.items", hasSize(0)));
        mvc.perform(get("/api/orders").with(CUSTOMER)).andExpect(jsonPath("$.items", hasSize(1)));

        mvc.perform(post("/api/orders/" + id + "/cancel").with(CUSTOMER).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(variants.findById(mediumId).orElseThrow().getStock()).isEqualTo(2);
        mvc.perform(post("/api/orders/" + id + "/cancel").with(CUSTOMER).with(csrf())).andExpect(status().isConflict());
    }

    // ---- Reviews ----

    @Test
    void onlyCustomersWhoReceivedTheProductCanReviewItOnce() throws Exception {
        long id = orderId(placeOrder(CUSTOMER, mediumId, 1));
        mvc.perform(get("/api/products/ao-thun-test/reviews/eligibility"))
                .andExpect(jsonPath("$.reason").value("LOGIN_REQUIRED"));
        mvc.perform(json(post("/api/products/ao-thun-test/reviews"), CUSTOMER, "{\"rating\":5}"))
                .andExpect(status().isForbidden());

        for (String status : new String[] {"CONFIRMED", "SHIPPING", "DELIVERED"}) {
            mvc.perform(json(patch("/api/admin/orders/" + id + "/status"), ADMIN, "{\"status\":\"" + status + "\"}"))
                    .andExpect(status().isOk());
        }
        mvc.perform(json(patch("/api/admin/orders/" + id + "/status"), ADMIN, "{\"status\":\"PENDING\"}"))
                .andExpect(status().isConflict());

        mvc.perform(get("/api/products/ao-thun-test/reviews/eligibility").with(CUSTOMER))
                .andExpect(jsonPath("$.canReview").value(true));
        mvc.perform(json(post("/api/products/ao-thun-test/reviews"), CUSTOMER, "{\"rating\":5,\"comment\":\"Đẹp\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.authorName").value("Khách Hàng"));
        mvc.perform(json(post("/api/products/ao-thun-test/reviews"), CUSTOMER, "{\"rating\":4}"))
                .andExpect(status().isConflict());
        mvc.perform(json(post("/api/products/ao-thun-test/reviews"), OTHER, "{\"rating\":1}"))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/products/ao-thun-test"))
                .andExpect(jsonPath("$.ratingCount").value(1))
                .andExpect(jsonPath("$.ratingAverage").value(5.0));
        mvc.perform(get("/api/products/ao-thun-test/reviews")).andExpect(jsonPath("$.items", hasSize(1)));
    }

    // ---- Admin ----

    @Test
    void adminApiRequiresAdminRole() throws Exception {
        mvc.perform(get("/api/admin/orders")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/orders").with(CUSTOMER))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Bạn không có quyền thực hiện thao tác này"));
        mvc.perform(get("/api/admin/stats").with(ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeProducts").value(1));
    }

    @Test
    void adminManagesProductsAndVariantIdsSurviveEdits() throws Exception {
        String body = """
                {"name":"Áo khoác mới","description":"Mô tả","price":500000,"compareAtPrice":600000,
                 "categoryId":%d,"images":["https://example.com/a.jpg"],"active":true,"featured":true,
                 "variants":[{"size":"M","color":"Đen","stock":5},{"size":"L","color":"Đen","stock":3}]}
                """.formatted(categoryId);
        String created = mvc.perform(json(post("/api/admin/products"), ADMIN, body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("ao-khoac-moi"))
                .andReturn().getResponse().getContentAsString();
        Number productId = JsonPath.read(created, "$.id");
        Number mId = JsonPath.read(created, "$.variants[0].id");

        // Re-sending a size/color without its id updates the same variant instead of replacing it,
        // and its stock is not overwritten from the product form (it only changes through the inventory).
        String update = """
                {"name":"Áo khoác mới","price":450000,"categoryId":%d,"active":true,"featured":false,
                 "variants":[{"size":"M","color":"Đen","stock":9}]}
                """.formatted(categoryId);
        mvc.perform(json(put("/api/admin/products/" + productId), ADMIN, update))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.variants", hasSize(1)))
                .andExpect(jsonPath("$.variants[0].id").value(mId))
                .andExpect(jsonPath("$.variants[0].stock").value(5))
                .andExpect(jsonPath("$.variants[0].sku").value("AO-KHOAC-MOI-DEN-M"));

        mvc.perform(json(post("/api/admin/products"), ADMIN, body.replace("600000", "100000")))
                .andExpect(status().isBadRequest());
        mvc.perform(json(post("/api/admin/products"), ADMIN,
                        body.replace("{\"size\":\"L\"", "{\"size\":\"M\"")))
                .andExpect(status().isBadRequest());

        mvc.perform(delete("/api/admin/products/" + productId).with(ADMIN).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/products/ao-khoac-moi")).andExpect(status().isNotFound());

        mvc.perform(delete("/api/admin/categories/" + categoryId).with(ADMIN).with(csrf()))
                .andExpect(status().isConflict());
    }

    // ---- Helpers ----

    private ResultActions placeOrder(RequestPostProcessor who, Long variantId, int quantity) throws Exception {
        return mvc.perform(json(post("/api/orders"), who, order(variantId, quantity)));
    }

    private static String order(Long variantId, int quantity) {
        return """
                {"items":[{"variantId":%d,"quantity":%d}],"recipientName":"Nguyễn Văn A",
                 "phone":"0901234567","address":"1 Lê Lợi, Quận 1, TP.HCM"}
                """.formatted(variantId, quantity);
    }

    private static long orderId(ResultActions result) throws Exception {
        Number id = JsonPath.read(result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(),
                "$.id");
        return id.longValue();
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, RequestPostProcessor who,
            String body) {
        return request.with(who).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body);
    }
}
