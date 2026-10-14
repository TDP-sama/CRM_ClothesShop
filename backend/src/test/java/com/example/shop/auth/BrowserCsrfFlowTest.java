package com.example.shop.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The exact sequence a browser SPA performs, using the real cookie-based CSRF repository.
 * Deliberately never uses spring-security-test's {@code csrf()} post-processor, which swaps the CSRF
 * repository of the (cached) context for a session-based one; hence the separate context (own DB name).
 */
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:csrfflow;DB_CLOSE_DELAY=-1", "app.seed-demo-data=false"})
@AutoConfigureMockMvc
class BrowserCsrfFlowTest {

    @Autowired
    private MockMvc mvc;

    private record Browser(MockHttpSession session, Cookie xsrf) {
    }

    @Test
    void loginIsCsrfProtectedAndRotatesTheToken() throws Exception {
        Cookie xsrf = fetchToken();
        register(xsrf, "heidi@example.com");

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(credentials("heidi@example.com")))
                .andExpect(status().isForbidden());

        Browser browser = login(xsrf, "heidi@example.com");
        assertThat(browser.xsrf().getValue()).isNotEqualTo(xsrf.getValue());
    }

    @Test
    void writesNeedMatchingHeaderAndLoginAfterLogoutNeedsFreshToken() throws Exception {
        Cookie xsrf = fetchToken();
        register(xsrf, "ivan@example.com");
        Browser browser = login(xsrf, "ivan@example.com");

        // A valid token reaches the controller (400: empty cart); a forged one is stopped by the filter (403).
        mvc.perform(json(post("/api/orders"), browser.xsrf(), "{\"items\":[]}").session(browser.session()))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/orders").session(browser.session()).cookie(browser.xsrf())
                        .header("X-XSRF-TOKEN", "forged")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"items\":[]}"))
                .andExpect(status().isForbidden());

        // Logout is CSRF-protected and clears the token cookie.
        mvc.perform(post("/api/auth/logout").session(browser.session())).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/logout").session(browser.session()).cookie(browser.xsrf())
                        .header("X-XSRF-TOKEN", browser.xsrf().getValue()))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("XSRF-TOKEN", 0));

        // The SPA then fetches a fresh token, and logging in again works.
        login(fetchToken(), "ivan@example.com");
    }

    private Cookie fetchToken() throws Exception {
        return mvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isNoContent())
                .andExpect(cookie().httpOnly("XSRF-TOKEN", false))
                .andReturn().getResponse().getCookie("XSRF-TOKEN");
    }

    private void register(Cookie xsrf, String email) throws Exception {
        mvc.perform(json(post("/api/auth/register"), xsrf,
                        "{\"fullName\":\"Test\",\"email\":\"%s\",\"password\":\"password123\"}".formatted(email)))
                .andExpect(status().isCreated());
    }

    private Browser login(Cookie xsrf, String email) throws Exception {
        MvcResult result = mvc.perform(json(post("/api/auth/login"), xsrf, credentials(email)))
                .andExpect(status().isOk())
                .andReturn();
        // The response first clears the old cookie, then sets the new one; the browser keeps the last.
        Cookie rotated = Arrays.stream(result.getResponse().getCookies())
                .filter(c -> c.getName().equals("XSRF-TOKEN") && !c.getValue().isEmpty())
                .reduce((first, second) -> second)
                .orElseThrow();
        return new Browser((MockHttpSession) result.getRequest().getSession(false), rotated);
    }

    private static String credentials(String email) {
        return "{\"email\":\"%s\",\"password\":\"password123\"}".formatted(email);
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, Cookie xsrf, String body) {
        return request.cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue())
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }
}
