package com.example.shop.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Full stack (security filters, validation, JPA, BCrypt) against an in-memory H2 database. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:authtest;DB_CLOSE_DELAY=-1",
        "app.seed-demo-data=false",
        "app.admin-emails=boss@example.com"})
@AutoConfigureMockMvc
class AuthIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AppUserRepository users;

    @BeforeEach
    void cleanDatabase() {
        users.deleteAll();
    }

    private ResultActions postJson(String url, String json) throws Exception {
        return mvc.perform(post(url).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions register(String email, String password) throws Exception {
        return postJson("/api/auth/register",
                "{\"fullName\":\"Nguyễn Văn A\",\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password));
    }

    private ResultActions login(String email, String password) throws Exception {
        return postJson("/api/auth/login", "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password));
    }

    @Test
    void registerCreatesCustomerWithHashedPassword() throws Exception {
        register("Alice@Example.com", "correct-horse")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.fullName").value("Nguyễn Văn A"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        AppUser saved = users.findByEmail("alice@example.com").orElseThrow();
        assertThat(saved.getPasswordHash()).startsWith("{bcrypt}").doesNotContain("correct-horse");
    }

    @Test
    void registerNormalizesEmailBeforeValidating() throws Exception {
        register("  Ivan@Example.COM ", "password123")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("ivan@example.com"));
    }

    @Test
    void emailsListedInAdminEmailsBecomeAdmins() throws Exception {
        register("Boss@example.com", "password123")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void registerRejectsDuplicateEmailCaseInsensitively() throws Exception {
        register("bob@example.com", "password123").andExpect(status().isCreated());

        register("BOB@example.com", "password456")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Email này đã được đăng ký"));
    }

    @Test
    void registerRejectsInvalidInputWithFieldErrors() throws Exception {
        register("not-an-email", "password123")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").value("Email không hợp lệ"));
        register("carol@example.com", "short")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").value("Mật khẩu cần ít nhất 8 ký tự"));
        // 30 Vietnamese letters = 90 UTF-8 bytes: over BCrypt's 72-byte limit, must be a 400 and not a 500.
        register("carol@example.com", "ệ".repeat(30))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").value("Mật khẩu quá dài"));
        postJson("/api/auth/register", "{}").andExpect(status().isBadRequest());
        postJson("/api/auth/register", "not json").andExpect(status().isBadRequest());
        assertThat(users.count()).isZero();
    }

    @Test
    void loginStartsSessionThatAuthorizesOrderApi() throws Exception {
        register("erin@example.com", "password123");

        mvc.perform(get("/api/orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.detail").value("Vui lòng đăng nhập"));
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());

        MockHttpSession session = (MockHttpSession) login("ERIN@example.com", "password123")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("erin@example.com"))
                .andReturn().getRequest().getSession(false);
        assertThat(session).isNotNull();

        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
        mvc.perform(get("/api/orders").session(session)).andExpect(status().isOk());

        mvc.perform(post("/api/auth/logout").with(csrf()).session(session))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousRequestsDoNotCreateSessions() throws Exception {
        var result = mvc.perform(get("/api/orders")).andExpect(status().isUnauthorized()).andReturn();
        assertThat(result.getRequest().getSession(false)).isNull();
    }

    @Test
    void sessionOfDeletedAccountIsEnded() throws Exception {
        register("judy@example.com", "password123");
        MockHttpSession session = (MockHttpSession) login("judy@example.com", "password123")
                .andReturn().getRequest().getSession(false);

        users.deleteAll();

        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void loginChangesSessionIdToPreventFixation() throws Exception {
        register("frank@example.com", "password123");
        MockHttpSession session = new MockHttpSession();
        String before = session.getId();

        mvc.perform(post("/api/auth/login").with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"frank@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isOk());

        assertThat(session.getId()).isNotEqualTo(before);
    }

    @Test
    void loginRejectsWrongPasswordAndUnknownEmailIdentically() throws Exception {
        register("grace@example.com", "password123");

        login("grace@example.com", "wrong-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Email hoặc mật khẩu không đúng"));
        login("nobody@example.com", "password123")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Email hoặc mật khẩu không đúng"));
        // Over-long input is answered like any other wrong password, not with a 500.
        login("grace@example.com", "ệ".repeat(60)).andExpect(status().isUnauthorized());
    }

    @Test
    void loginRejectsBlankFields() throws Exception {
        login("", "").andExpect(status().isBadRequest());
    }
}
