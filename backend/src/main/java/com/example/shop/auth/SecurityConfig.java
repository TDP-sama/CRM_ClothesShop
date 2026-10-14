package com.example.shop.auth;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;

/**
 * Stateful, cookie-based security for a same-origin SPA:
 * HttpOnly session cookie for authentication, double-submit XSRF-TOKEN cookie for CSRF protection.
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, CookieCsrfTokenRepository csrfTokenRepository,
            UserService users) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
                        // Storefront browsing is public; writing a review is not.
                        .requestMatchers(HttpMethod.GET, "/api/config", "/api/categories", "/api/products",
                                "/api/products/**").permitAll()
                        // Admin area: each part requires its section (STAFF) or ADMIN; accounts/permissions are ADMIN only.
                        .requestMatchers("/api/admin/stats").access(section(users, AdminSection.DASHBOARD))
                        .requestMatchers("/api/admin/products", "/api/admin/products/**")
                                .access(section(users, AdminSection.PRODUCTS))
                        .requestMatchers("/api/admin/categories", "/api/admin/categories/**")
                                .access(section(users, AdminSection.CATEGORIES))
                        .requestMatchers("/api/admin/orders", "/api/admin/orders/**")
                                .access(section(users, AdminSection.ORDERS))
                        .requestMatchers("/api/admin/inventory/**").access(section(users, AdminSection.INVENTORY))
                        .requestMatchers("/api/admin/reports", "/api/admin/reports/**")
                                .access(section(users, AdminSection.REPORTS))
                        .requestMatchers("/api/admin/**").access(adminOnly(users))
                        // API docs only; the endpoints they describe keep their own rules.
                        .requestMatchers(HttpMethod.GET, "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        // Everything else is the bundled React app (HTML/JS/CSS); its data still comes from /api.
                        .anyRequest().permitAll())
                // Cookie XSRF-TOKEN (readable by JS) + header X-XSRF-TOKEN; also covers login/register (login CSRF).
                .csrf(csrf -> csrf.spa().csrfTokenRepository(csrfTokenRepository))
                // A JSON API never redirects back to a saved request, and saving one would create a session
                // for every anonymous 401.
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, e) ->
                                writeProblem(response, HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập"))
                        .accessDeniedHandler((request, response, e) ->
                                writeProblem(response, HttpStatus.FORBIDDEN, "Bạn không có quyền thực hiện thao tác này")))
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)));
        return http.build();
    }

    /**
     * Looks the permission up in the database rather than in the session's authorities, so changes an admin
     * makes apply to the staff member's very next request.
     */
    private static AuthorizationManager<RequestAuthorizationContext> section(UserService users, AdminSection section) {
        return (authentication, context) -> new AuthorizationDecision(
                isLoggedIn(authentication.get()) && users.canAccess(authentication.get().getName(), section));
    }

    private static AuthorizationManager<RequestAuthorizationContext> adminOnly(UserService users) {
        return (authentication, context) -> new AuthorizationDecision(
                isLoggedIn(authentication.get()) && users.isAdmin(authentication.get().getName()));
    }

    private static boolean isLoggedIn(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    /** Shared with {@link AuthController} so login can rotate the CSRF token. */
    @Bean
    CookieCsrfTokenRepository csrfTokenRepository() {
        return CookieCsrfTokenRepository.withHttpOnlyFalse();
    }

    /** Delegating encoder: BCrypt today, and stored hashes carry an {id} prefix so the algorithm can be upgraded. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    /** Same problem+json shape as MVC errors, for 401/403 produced by the security filters. */
    private static void writeProblem(HttpServletResponse response, HttpStatus status, String detail) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/problem+json;charset=UTF-8");
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"%s\",\"status\":%d,\"detail\":\"%s\"}"
                .formatted(status.getReasonPhrase(), status.value(), detail));
    }
}
