package com.example.shop.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Order emails go out after commit, asynchronously, to the customer. The SMTP sender is a mock. */
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:mail;DB_CLOSE_DELAY=-1", "app.seed-demo-data=false"})
@AutoConfigureMockMvc
class OrderMailerIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private AppUserRepository users;
    @Autowired private CategoryRepository categories;
    @Autowired private ProductRepository products;
    @Autowired private OrderRepository orders;
    @Autowired private StockMovementRepository movements;

    @MockitoBean private JavaMailSender mailSender;

    private Long variantId;

    @BeforeEach
    void setUp() {
        movements.deleteAll();
        orders.deleteAll();
        products.deleteAll();
        categories.deleteAll();
        users.deleteAll();
        users.save(new AppUser("buyer@example.com", "{noop}x", "Người Mua", Role.CUSTOMER));
        users.save(new AppUser("admin@example.com", "{noop}x", "Admin", Role.ADMIN));
        Product p = new Product("Áo thun", "ao-thun", categories.save(new Category("Áo", "ao", null, 1)));
        p.setPrice(150_000);
        p.addVariant("M", "Đen", 10);
        variantId = products.save(p).getVariants().get(0).getId();
        when(mailSender.createMimeMessage()).thenAnswer(inv -> new MimeMessage((Session) null));
    }

    @Test
    void customerIsEmailedWhenOrderIsPlacedAndWhenItShips() throws Exception {
        String order = mvc.perform(post("/api/orders").with(user("buyer@example.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"items":[{"variantId":%d,"quantity":2}],"recipientName":"Người Mua",
                                 "phone":"0901234567","address":"1 Lê Lợi, Q1"}
                                """.formatted(variantId)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String code = JsonPath.read(order, "$.code");
        Number id = JsonPath.read(order, "$.id");

        mvc.perform(patch("/api/admin/orders/" + id + "/status").with(user("admin@example.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CONFIRMED\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, timeout(5000).times(2)).send(sent.capture());
        List<MimeMessage> messages = sent.getAllValues();
        assertThat(messages).extracting(MimeMessage::getSubject)
                .containsExactlyInAnyOrder("Xác nhận đơn hàng " + code, "Đơn hàng " + code + ": Đã xác nhận");
        for (MimeMessage message : messages) {
            assertThat(message.getAllRecipients()[0].toString()).isEqualTo("buyer@example.com");
        }
    }

    @Test
    void noEmailWhenTheOrderIsRejected() throws Exception {
        // More than the 10 in stock: the order is refused, so nothing is committed and nothing is emailed.
        mvc.perform(post("/api/orders").with(user("buyer@example.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"items":[{"variantId":%d,"quantity":15}],"recipientName":"A","phone":"0901234567","address":"HN"}
                                """.formatted(variantId)))
                .andExpect(status().isConflict());
        Thread.sleep(500);
        verify(mailSender, times(0)).send(any(MimeMessage.class));
    }
}
