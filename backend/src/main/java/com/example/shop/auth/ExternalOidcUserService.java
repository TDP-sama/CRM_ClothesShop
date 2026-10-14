package com.example.shop.auth;

import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

/**
 * Turns a "Sign in with Google" login (brokered by Keycloak) into the shop's own account: the principal's name is
 * the account email, exactly as for password logins, so sessions, {@code /api/auth/me} and the admin permission
 * checks (which look the email up in the database) work unchanged.
 */
@Component
public class ExternalOidcUserService extends OidcUserService {

    private final UserService users;

    public ExternalOidcUserService(UserService users) {
        this.users = users;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest request) {
        OidcUser external = super.loadUser(request);
        AppUser account;
        try {
            account = users.findOrCreateExternal(external.getEmail(), Boolean.TRUE.equals(external.getEmailVerified()),
                    external.getFullName());
        } catch (IllegalArgumentException e) {
            throw new OAuth2AuthenticationException(new OAuth2Error("email_not_verified"), e.getMessage());
        }
        return new DefaultOidcUser(List.of(new SimpleGrantedAuthority("ROLE_" + account.getRole().name())),
                external.getIdToken(), external.getUserInfo(), "email");
    }
}
