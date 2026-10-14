package com.example.shop.auth;

/**
 * Parts of the admin area that can be granted to STAFF accounts. Managing accounts and permissions is not
 * a section: it is reserved for ADMIN.
 */
public enum AdminSection {
    DASHBOARD,
    PRODUCTS,
    CATEGORIES,
    ORDERS,
    INVENTORY,
    REPORTS
}
