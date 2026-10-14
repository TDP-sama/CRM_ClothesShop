package com.example.shop.report;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.shop.auth.AppUser;
import com.example.shop.auth.AppUserRepository;
import com.example.shop.auth.Role;
import com.example.shop.catalog.Category;
import com.example.shop.catalog.CategoryRepository;
import com.example.shop.catalog.Product;
import com.example.shop.catalog.ProductRepository;
import com.example.shop.inventory.StockMovementRepository;
import com.example.shop.order.OrderRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Sales report numbers from real orders placed through the API, against H2. */
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:reports;DB_CLOSE_DELAY=-1", "app.seed-demo-data=false"})
@AutoConfigureMockMvc
class ReportIntegrationTest {

    private static final RequestPostProcessor ADMIN = user("admin@example.com");
    private static final RequestPostProcessor CUSTOMER = user("customer@example.com");

    @Autowired private MockMvc mvc;
    @Autowired private AppUserRepository users;
    @Autowired private CategoryRepository categories;
    @Autowired private ProductRepository products;
    @Autowired private OrderRepository orders;
    @Autowired private StockMovementRepository movements;

    private Long teeVariant;
    private Long dressVariant;

    @BeforeEach
    void setUp() {
        movements.deleteAll();
        orders.deleteAll();
        products.deleteAll();
        categories.deleteAll();
        users.deleteAll();
        users.save(new AppUser("admin@example.com", "{noop}x", "Admin", Role.ADMIN));
        users.save(new AppUser("customer@example.com", "{noop}x", "Khách", Role.CUSTOMER));
        teeVariant = product("Áo thun", "ao-thun", categories.save(new Category("Áo", "ao", null, 1)), 100_000);
        dressVariant = product("Đầm lụa", "dam-lua", categories.save(new Category("Đầm", "dam", null, 2)), 450_000);
    }

    private Long product(String name, String slug, Category category, long price) {
        Product p = new Product(name, slug, category);
        p.setPrice(price);
        p.addVariant("M", "Đen", 50);
        return products.save(p).getVariants().get(0).getId();
    }

    private String order(Long variantId, int quantity) throws Exception {
        return mvc.perform(post("/api/orders").with(CUSTOMER).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"variantId":%d,"quantity":%d}],"recipientName":"A","phone":"0901234567","address":"HN"}
                                """.formatted(variantId, quantity)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    }

    @Test
    void reportSumsNonCancelledOrdersByProductAndCategory() throws Exception {
        order(teeVariant, 3);
        order(teeVariant, 2);
        order(dressVariant, 1);
        Number cancelled = JsonPath.read(order(dressVariant, 4), "$.id");
        mvc.perform(post("/api/orders/" + cancelled + "/cancel").with(CUSTOMER).with(csrf()))
                .andExpect(status().isOk());

        mvc.perform(get("/api/admin/reports").with(ADMIN).param("days", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.granularity").value("DAY"))
                .andExpect(jsonPath("$.series", hasSize(7)))
                .andExpect(jsonPath("$.series[6].revenue").value(950_000))
                .andExpect(jsonPath("$.current.revenue").value(950_000))
                .andExpect(jsonPath("$.current.orders").value(3))
                .andExpect(jsonPath("$.current.units").value(6))
                .andExpect(jsonPath("$.current.averageOrderValue").value(316_667))
                .andExpect(jsonPath("$.previous.orders").value(0))
                .andExpect(jsonPath("$.topProducts[0].name").value("Áo thun"))
                .andExpect(jsonPath("$.topProducts[0].units").value(5))
                .andExpect(jsonPath("$.topProducts[1].revenue").value(450_000))
                .andExpect(jsonPath("$.categories[0].name").value("Áo"))
                .andExpect(jsonPath("$.categories[0].revenue").value(500_000));

        mvc.perform(get("/api/admin/reports").with(ADMIN).param("days", "365"))
                .andExpect(jsonPath("$.granularity").value("MONTH"));
        mvc.perform(get("/api/admin/reports").with(ADMIN).param("days", "12")).andExpect(status().isBadRequest());
    }

    @Test
    void reportsNeedTheReportsSection() throws Exception {
        mvc.perform(get("/api/admin/reports").with(CUSTOMER)).andExpect(status().isForbidden());
    }
}
