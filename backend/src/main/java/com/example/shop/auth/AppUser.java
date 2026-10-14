package com.example.shop.auth;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import org.hibernate.annotations.ColumnDefault;

/** A registered user. Table is {@code app_users} because {@code user} is a reserved word in PostgreSQL. */
@Entity
@Table(name = "app_users")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Stored normalized (trimmed, lower-case); the unique constraint is the source of truth for duplicates. */
    @Column(nullable = false, unique = true, length = 254)
    private String email;

    /** Encoded hash with algorithm prefix, e.g. {@code {bcrypt}$2a$10$...}. Never the raw password. */
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    // Nullable so ddl-auto can add the column to a table that already has rows.
    @Column(name = "full_name", length = 100)
    private String fullName;

    // The default lets ddl-auto add this NOT NULL column to an existing table.
    @Enumerated(EnumType.STRING)
    @ColumnDefault("'CUSTOMER'")
    @Column(nullable = false, length = 20)
    private Role role = Role.CUSTOMER;

    /** Admin sections a STAFF account may use; empty for other roles (ADMIN implicitly has all). */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_permissions", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "section", nullable = false, length = 30)
    private Set<AdminSection> permissions = new HashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected AppUser() {
    }

    /** Whether this account may enter the admin area at all. */
    public boolean isBackOffice() {
        return role == Role.ADMIN || role == Role.STAFF;
    }

    public boolean canAccess(AdminSection section) {
        return role == Role.ADMIN || (role == Role.STAFF && permissions.contains(section));
    }

    /** The sections this account can actually use, as the UI should show them. */
    public Set<AdminSection> getEffectivePermissions() {
        if (role == Role.ADMIN) {
            return EnumSet.allOf(AdminSection.class);
        }
        return role == Role.STAFF && !permissions.isEmpty() ? EnumSet.copyOf(permissions) : EnumSet.noneOf(AdminSection.class);
    }

    /** Sets role and, for STAFF, the granted sections; other roles never keep stale permissions. */
    void setAccess(Role role, Set<AdminSection> permissions) {
        this.role = role;
        this.permissions.clear();
        if (role == Role.STAFF) {
            this.permissions.addAll(permissions);
        }
    }

    public AppUser(String email, String passwordHash, String fullName, Role role) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.role = role;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    /** Name shown on reviews and orders; falls back to the email's local part for accounts created without one. */
    public String getDisplayName() {
        return fullName != null && !fullName.isBlank() ? fullName : email.substring(0, email.indexOf('@'));
    }

    public Role getRole() {
        return role;
    }

    /** Promotion to ADMIN (from ADMIN_EMAILS); clears staff permissions, which ADMIN does not need. */
    public void makeAdmin() {
        setAccess(Role.ADMIN, Set.of());
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
