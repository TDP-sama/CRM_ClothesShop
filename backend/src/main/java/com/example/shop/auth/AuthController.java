package com.example.shop.auth;

import com.example.shop.common.MaxUtf8Bytes;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Session-cookie authentication for the SPA. Logout is {@code POST /api/auth/logout}, handled by
 * Spring Security (see {@link SecurityConfig}); afterwards the SPA calls {@code GET /api/auth/csrf} again.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /** Email is normalized before validation, so pasted/autofilled spaces and capitals are accepted. */
    record RegisterRequest(
            @NotBlank(message = "Vui lòng nhập họ tên") @Size(max = 100, message = "Họ tên tối đa 100 ký tự") String fullName,
            @NotBlank(message = "Vui lòng nhập email") @Email(message = "Email không hợp lệ")
            @Size(max = 254, message = "Email quá dài") String email,
            @NotBlank(message = "Vui lòng nhập mật khẩu") @Size(min = 8, message = "Mật khẩu cần ít nhất 8 ký tự")
            @MaxUtf8Bytes(value = 72, message = "Mật khẩu quá dài") String password) {

        RegisterRequest {
            fullName = fullName == null ? null : fullName.trim();
            email = email == null ? null : UserService.normalizeEmail(email);
        }
    }

    record LoginRequest(
            @NotBlank(message = "Vui lòng nhập email") @Size(max = 254) String email,
            @NotBlank(message = "Vui lòng nhập mật khẩu") @Size(max = 200) String password) {
    }

    /** {@code permissions}: the admin sections this account can open (all of them for ADMIN). */
    record UserResponse(Long id, String email, String fullName, Role role, Set<AdminSection> permissions) {
        static UserResponse of(AppUser user) {
            return new UserResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.getRole(),
                    user.getEffectivePermissions());
        }
    }

    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final CookieCsrfTokenRepository csrfTokenRepository;
    private final ObjectProvider<ClientRegistrationRepository> clientRegistrations;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();
    private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();

    public AuthController(UserService userService, AuthenticationManager authenticationManager,
            CookieCsrfTokenRepository csrfTokenRepository,
            ObjectProvider<ClientRegistrationRepository> clientRegistrations) {
        this.userService = userService;
        this.authenticationManager = authenticationManager;
        this.csrfTokenRepository = csrfTokenRepository;
        this.clientRegistrations = clientRegistrations;
    }

    /** Which external sign-in options exist, so the SPA shows the Google button only when it works. */
    @GetMapping("/providers")
    public Map<String, Boolean> providers() {
        return Map.of("google", clientRegistrations.getIfAvailable() != null);
    }

    /** Sets the {@code XSRF-TOKEN} cookie; the SPA echoes it in the {@code X-XSRF-TOKEN} header on writes. */
    @GetMapping("/csrf")
    public ResponseEntity<Void> csrf(CsrfToken token) {
        token.getToken(); // touching the deferred token makes the repository write the cookie
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return UserResponse.of(userService.register(request.email(), request.password(), request.fullName()));
    }

    @PostMapping("/login")
    public UserResponse login(@Valid @RequestBody LoginRequest request,
                              HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String email = UserService.normalizeEmail(request.email());
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(email, request.password()));
        } catch (InternalAuthenticationServiceException e) {
            throw e; // e.g. database down: a server error, not "wrong password"
        } catch (AuthenticationException e) {
            // Same message for unknown email and wrong password: no account enumeration via login.
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email hoặc mật khẩu không đúng");
        }

        // Prevent session fixation: never reuse a pre-login session id.
        if (httpRequest.getSession(false) != null) {
            httpRequest.changeSessionId();
        }
        SecurityContext context = contextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        contextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        // Login is handled here rather than by a Spring login filter, so rotate the CSRF token ourselves:
        // a token planted before login must not survive it.
        csrfTokenRepository.saveToken(null, httpRequest, httpResponse);
        csrfTokenRepository.saveToken(csrfTokenRepository.generateToken(httpRequest), httpRequest, httpResponse);

        return UserResponse.of(userService.require(email));
    }

    /** Lets the SPA find out on startup whether it already has a valid session (200) or not (401). */
    @GetMapping("/me")
    public UserResponse me(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        try {
            return UserResponse.of(userService.require(authentication.getName()));
        } catch (ResponseStatusException e) {
            // The account was deleted after login: end the stale session instead of leaving it half-valid.
            new SecurityContextLogoutHandler().logout(request, response, authentication);
            throw e;
        }
    }
}
