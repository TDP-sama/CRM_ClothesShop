package com.example.shop.auth;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Staff permissions are read from the database on every request, so the mock principals below carry no roles
 * at all: what they may do depends only on their AppUser row.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:access;DB_CLOSE_DELAY=-1",
        "app.seed-demo-data=false",
        "app.admin-emails=boss@example.com"})
@AutoConfigureMockMvc
class AccessControlIntegrationTest {

    private static final RequestPostProcessor ADMIN = user("boss@example.com");
    private static final RequestPostProcessor STAFF = user("staff@example.com");
    private static final RequestPostProcessor CUSTOMER = user("buyer@example.com");

    @Autowired private MockMvc mvc;
    @Autowired private AppUserRepository users;

    private Long staffId;
    private Long adminId;

    @BeforeEach
    void setUp() {
        users.deleteAll();
        adminId = users.save(new AppUser("boss@example.com", "{noop}x", "Sếp", Role.ADMIN)).getId();
        staffId = users.save(new AppUser("staff@example.com", "{noop}x", "Nhân Viên", Role.CUSTOMER)).getId();
        users.save(new AppUser("buyer@example.com", "{noop}x", "Khách", Role.CUSTOMER));
    }

    private ResultActions setAccess(RequestPostProcessor who, Long id, String body) throws Exception {
        return mvc.perform(put("/api/admin/users/" + id + "/access").with(who).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void customersCannotEnterTheAdminArea() throws Exception {
        mvc.perform(get("/api/admin/orders").with(CUSTOMER)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/stats").with(CUSTOMER)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/orders")).andExpect(status().isUnauthorized());
    }

    @Test
    void staffOnlyReachTheSectionsTheyWereGrantedAndChangesApplyImmediately() throws Exception {
        setAccess(ADMIN, staffId, "{\"role\":\"STAFF\",\"permissions\":[\"ORDERS\"]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("STAFF"))
                .andExpect(jsonPath("$.permissions", containsInAnyOrder("ORDERS")));

        mvc.perform(get("/api/admin/orders").with(STAFF)).andExpect(status().isOk());
        mvc.perform(get("/api/admin/products").with(STAFF)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/stats").with(STAFF)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users").with(STAFF)).andExpect(status().isForbidden());

        // Granting more takes effect on the next request, without a new login.
        setAccess(ADMIN, staffId, "{\"role\":\"STAFF\",\"permissions\":[\"ORDERS\",\"PRODUCTS\"]}");
        mvc.perform(get("/api/admin/products").with(STAFF)).andExpect(status().isOk());

        // Demoting to customer removes all access and stored permissions.
        setAccess(ADMIN, staffId, "{\"role\":\"CUSTOMER\",\"permissions\":[\"ORDERS\"]}")
                .andExpect(jsonPath("$.permissions", hasSize(0)));
        mvc.perform(get("/api/admin/orders").with(STAFF)).andExpect(status().isForbidden());
    }

    @Test
    void meReportsEffectivePermissions() throws Exception {
        setAccess(ADMIN, staffId, "{\"role\":\"STAFF\",\"permissions\":[\"CATEGORIES\"]}");
        mvc.perform(get("/api/auth/me").with(STAFF))
                .andExpect(jsonPath("$.role").value("STAFF"))
                .andExpect(jsonPath("$.permissions", containsInAnyOrder("CATEGORIES")));
        mvc.perform(get("/api/auth/me").with(ADMIN))
                .andExpect(jsonPath("$.permissions", hasSize(AdminSection.values().length)));
    }

    @Test
    void onlyAdminsManageAccessAndAdminsCannotBeChanged() throws Exception {
        setAccess(ADMIN, staffId, "{\"role\":\"STAFF\",\"permissions\":[\"ORDERS\"]}");
        setAccess(STAFF, staffId, "{\"role\":\"STAFF\",\"permissions\":[\"ORDERS\",\"PRODUCTS\"]}")
                .andExpect(status().isForbidden());

        setAccess(ADMIN, adminId, "{\"role\":\"CUSTOMER\"}").andExpect(status().isConflict());
        setAccess(ADMIN, staffId, "{\"role\":\"ADMIN\"}").andExpect(status().isBadRequest());
        setAccess(ADMIN, staffId, "{}").andExpect(status().isBadRequest());

        mvc.perform(get("/api/admin/users").with(ADMIN).param("role", "STAFF"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].email").value("staff@example.com"));
        mvc.perform(get("/api/admin/users").with(ADMIN).param("q", "boss"))
                .andExpect(jsonPath("$.items[0].locked").value(true));
    }
}
