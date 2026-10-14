package com.example.shop.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.web.servlet.MockMvc;

/** How Google sign-ins (via Keycloak) map onto local accounts. Keycloak itself is not involved here. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:external;DB_CLOSE_DELAY=-1",
        "app.seed-demo-data=false",
        "app.admin-emails=boss@example.com"})
@AutoConfigureMockMvc
class ExternalLoginTest {

    @Autowired private MockMvc mvc;
    @Autowired private UserService users;
    @Autowired private AppUserRepository repository;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    @Test
    void googleSignInLinksToTheExistingAccountWithThatEmail() throws Exception {
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Lan\",\"email\":\"lan@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated());
        Long existingId = repository.findByEmail("lan@example.com").orElseThrow().getId();

        AppUser linked = users.findOrCreateExternal("Lan@Example.com", true, "Lan Nguyễn");

        assertThat(linked.getId()).isEqualTo(existingId);
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void newGoogleUsersBecomeCustomersWithoutAUsablePassword() throws Exception {
        AppUser created = users.findOrCreateExternal("minh@gmail.com", true, "Minh Trần");

        assertThat(created.getRole()).isEqualTo(Role.CUSTOMER);
        assertThat(created.getFullName()).isEqualTo("Minh Trần");
        assertThat(created.getPasswordHash()).startsWith("{bcrypt}");
        // Password login for a Google-only account fails like any wrong password (no 500, no empty password).
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"minh@gmail.com\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"minh@gmail.com\",\"password\":\"password123\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminEmailsBecomeAdminsAndUnverifiedEmailsAreRefused() {
        assertThat(users.findOrCreateExternal("boss@example.com", true, null).getRole()).isEqualTo(Role.ADMIN);
        assertThatThrownBy(() -> users.findOrCreateExternal("victim@example.com", false, "X"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(repository.findByEmail("victim@example.com")).isEmpty();
    }

    /** Guards src/test/resources/config: tests must never pick up a real SMTP server from backend/.env. */
    @Test
    void testsNeverUseTheRealMailServerOrProfilesFromDotEnv(@Autowired ApplicationContext context) {
        assertThat(context.getBeanProvider(JavaMailSender.class).getIfAvailable()).isNull();
        assertThat(context.getEnvironment().getActiveProfiles()).isEmpty();
    }

    @Test
    void googleButtonIsHiddenWithoutTheKeycloakProfile() throws Exception {
        mvc.perform(get("/api/auth/providers")).andExpect(status().isOk()).andExpect(jsonPath("$.google").value(false));
    }
}
