package com.example.shop.auth;

/**
 * CUSTOMER shops; STAFF enters the admin area but only the {@link AdminSection}s granted to them;
 * ADMIN can do everything, including managing staff. ADMIN comes only from {@code ADMIN_EMAILS}.
 */
public enum Role {
    CUSTOMER,
    STAFF,
    ADMIN
}
