package com.example.shop.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.shop.auth.AdminSection;
import com.example.shop.auth.AppUser;
import com.example.shop.auth.AppUserRepository;
import com.example.shop.auth.Role;
import com.example.shop.auth.UserService;
import com.example.shop.catalog.Category;
import com.example.shop.catalog.CategoryRepository;
import com.example.shop.catalog.Product;
import com.example.shop.catalog.ProductRepository;
import com.example.shop.catalog.ProductVariantRepository;
import com.example.shop.order.OrderRepository;
import com.jayway.jsonpath.JsonPath;
import java.util.Set;
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

/** Receipts, adjustments, the stock ledger and how orders feed it, against H2. */
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:inventory;DB_CLOSE_DELAY=-1", "app.seed-demo-data=false"})
@AutoConfigureMockMvc
class InventoryIntegrationTest {

    private static final RequestPostProcessor ADMIN = user("admin@example.com");
    private static final RequestPostProcessor STOREKEEPER = user("kho@example.com");
    private static final RequestPostProcessor CUSTOMER = user("customer@example.com");

    @Autowired private MockMvc mvc;
    @Autowired private AppUserRepository users;
    @Autowired private UserService userService;
    @Autowired private CategoryRepository categories;
    @Autowired private ProductRepository products;
    @Autowired private ProductVariantRepository variants;
    @Autowired private StockMovementRepository movements;
    @Autowired private GoodsReceiptRepository receipts;
    @Autowired private OrderRepository orders;
    @Autowired private InventoryService inventory;

    private Long variantId;

    @BeforeEach
    void setUp() throws Exception {
        movements.deleteAll();
        receipts.deleteAll();
        orders.deleteAll();
        products.deleteAll();
        categories.deleteAll();
        users.deleteAll();
        users.save(new AppUser("admin@example.com", "{noop}x", "Admin", Role.ADMIN));
        users.save(new AppUser("customer@example.com", "{noop}x", "Khách", Role.CUSTOMER));
        Long keeperId = users.save(new AppUser("kho@example.com", "{noop}x", "Thủ kho", Role.CUSTOMER)).getId();
        userService.updateAccess(keeperId, Role.STAFF, Set.of(AdminSection.INVENTORY));

        Product tee = new Product("Áo thun kho", "ao-thun-kho", categories.save(new Category("Áo", "ao", null, 1)));
        tee.setPrice(100_000);
        tee.addVariant("M", "Đen", 10);
        products.save(tee);
        variantId = tee.getVariants().get(0).getId();
        inventory.run(null); // what startup does: SKU + opening balance for pre-existing stock
    }

    private ResultActions send(MockHttpServletRequestBuilder request, RequestPostProcessor who, String body)
            throws Exception {
        return mvc.perform(request.with(who).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private int stock() {
        return variants.findById(variantId).orElseThrow().getStock();
    }

    @Test
    void existingStockGetsSkuAndOpeningBalance() throws Exception {
        mvc.perform(get("/api/admin/inventory/movements").with(ADMIN).param("variantId", variantId.toString()))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].type").value("INITIAL"))
                .andExpect(jsonPath("$.items[0].stockAfter").value(10));
        assertThat(variants.findById(variantId).orElseThrow().getSku()).isEqualTo("AO-THUN-KHO-DEN-M");
        inventory.run(null); // idempotent
        assertThat(movements.count()).isEqualTo(1);
    }

    @Test
    void receiptAddsStockAndIsRecorded() throws Exception {
        String body = """
                {"supplier":"Xưởng May A","note":"Lô tháng 10",
                 "lines":[{"variantId":%d,"quantity":5},{"variantId":%d,"quantity":3}]}
                """.formatted(variantId, variantId);
        String created = send(post("/api/admin/inventory/receipts"), STOREKEEPER, body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.startsWith("PN")))
                .andExpect(jsonPath("$.totalQuantity").value(8))
                .andExpect(jsonPath("$.lines", hasSize(1)))
                .andReturn().getResponse().getContentAsString();
        assertThat(stock()).isEqualTo(18);

        mvc.perform(get("/api/admin/inventory/movements").with(ADMIN).param("type", "RECEIPT"))
                .andExpect(jsonPath("$.items[0].quantityChange").value(8))
                .andExpect(jsonPath("$.items[0].stockBefore").value(10))
                .andExpect(jsonPath("$.items[0].referenceCode").value(JsonPath.<String>read(created, "$.code")))
                .andExpect(jsonPath("$.items[0].actorEmail").value("kho@example.com"));

        send(post("/api/admin/inventory/receipts"), ADMIN, "{\"lines\":[{\"variantId\":999999,\"quantity\":1}]}")
                .andExpect(status().isNotFound());
        send(post("/api/admin/inventory/receipts"), ADMIN, "{\"lines\":[]}").andExpect(status().isBadRequest());
    }

    @Test
    void adjustmentSetsCountedStockAndRecordsTheDifference() throws Exception {
        send(post("/api/admin/inventory/adjustments"), ADMIN,
                "{\"variantId\":%d,\"actualQuantity\":7,\"reason\":\"DAMAGED\",\"note\":\"3 áo bị lỗi\"}".formatted(variantId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantityChange").value(-3))
                .andExpect(jsonPath("$.reason").value("DAMAGED"));
        assertThat(stock()).isEqualTo(7);

        send(post("/api/admin/inventory/adjustments"), ADMIN,
                "{\"variantId\":%d,\"actualQuantity\":7,\"reason\":\"COUNT\"}".formatted(variantId))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ordersAndCancellationsAreRecordedInTheLedger() throws Exception {
        String order = send(post("/api/orders"), CUSTOMER, """
                {"items":[{"variantId":%d,"quantity":4}],"recipientName":"A","phone":"0901234567","address":"HN"}
                """.formatted(variantId)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        assertThat(stock()).isEqualTo(6);
        Number orderId = JsonPath.read(order, "$.id");

        send(patch("/api/admin/orders/" + orderId + "/status"), ADMIN, "{\"status\":\"CANCELLED\"}")
                .andExpect(status().isOk());
        assertThat(stock()).isEqualTo(10);

        mvc.perform(get("/api/admin/inventory/movements").with(ADMIN).param("variantId", variantId.toString()))
                .andExpect(jsonPath("$.items", hasSize(3)))
                .andExpect(jsonPath("$.items[0].type").value("RETURN"))
                .andExpect(jsonPath("$.items[0].actorEmail").value("admin@example.com"))
                .andExpect(jsonPath("$.items[1].type").value("SALE"))
                .andExpect(jsonPath("$.items[1].quantityChange").value(-4))
                .andExpect(jsonPath("$.items[1].referenceCode").value(JsonPath.<String>read(order, "$.code")));
    }

    @Test
    void stockStatusFiltersAndSettings() throws Exception {
        send(patch("/api/admin/inventory/items/" + variantId), ADMIN, "{\"sku\":\"tee-black-m\",\"lowStockThreshold\":12}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("TEE-BLACK-M"))
                .andExpect(jsonPath("$.status").value("LOW"));
        mvc.perform(get("/api/admin/inventory/items").with(ADMIN).param("status", "LOW"))
                .andExpect(jsonPath("$.items", hasSize(1)));
        mvc.perform(get("/api/admin/inventory/items").with(ADMIN).param("q", "tee-black"))
                .andExpect(jsonPath("$.items", hasSize(1)));
        mvc.perform(get("/api/admin/inventory/summary").with(ADMIN))
                .andExpect(jsonPath("$.totalUnits").value(10))
                .andExpect(jsonPath("$.lowStock").value(1));
        mvc.perform(get("/api/admin/stats").with(ADMIN)).andExpect(jsonPath("$.lowStockVariants").value(1));
    }

    @Test
    void inventoryNeedsTheInventorySection() throws Exception {
        mvc.perform(get("/api/admin/inventory/items").with(STOREKEEPER)).andExpect(status().isOk());
        mvc.perform(get("/api/admin/orders").with(STOREKEEPER)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/inventory/items").with(CUSTOMER)).andExpect(status().isForbidden());
    }
}
