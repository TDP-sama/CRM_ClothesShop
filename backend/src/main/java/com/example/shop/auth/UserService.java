package com.example.shop.auth;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Registration, the {@link UserDetailsService} Spring Security uses to verify logins, lookup of the
 * logged-in user for other features, and promotion of the emails in {@code app.admin-emails} to ADMIN.
 */
@Service
public class UserService implements UserDetailsService, ApplicationRunner {

    private final AppUserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final Set<String> adminEmails;

    public UserService(AppUserRepository repository, PasswordEncoder passwordEncoder,
            @Value("${app.admin-emails:}") String adminEmails) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmails = Arrays.stream(adminEmails.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(UserService::normalizeEmail)
                .collect(Collectors.toUnmodifiableSet());
    }

    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public AppUser register(String email, String rawPassword, String fullName) {
        String normalized = normalizeEmail(email);
        if (repository.existsByEmail(normalized)) {
            throw new EmailTakenException();
        }
        Role role = adminEmails.contains(normalized) ? Role.ADMIN : Role.CUSTOMER;
        try {
            return repository.saveAndFlush(new AppUser(normalized, passwordEncoder.encode(rawPassword), fullName, role));
        } catch (DataIntegrityViolationException e) {
            // Only a concurrent registration of the same email is a conflict; anything else is a real error.
            if (repository.existsByEmail(normalized)) {
                throw new EmailTakenException();
            }
            throw e;
        }
    }

    /** The account behind an authenticated principal name (its email). */
    public AppUser require(String email) {
        return repository.findByEmail(normalizeEmail(email))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Tài khoản không còn tồn tại"));
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        AppUser user = repository.findByEmail(normalizeEmail(email))
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        return User.withUsername(user.getEmail())
                .password(user.getPasswordHash())
                .roles(user.getRole().name())
                .build();
    }

    /** Accounts registered before their email was added to ADMIN_EMAILS become admins on the next start. */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!adminEmails.isEmpty()) {
            repository.findByEmailIn(adminEmails).forEach(AppUser::makeAdmin);
        }
    }

    /**
     * Checked against the database on every admin request (not the role cached in the session), so granting or
     * revoking access takes effect immediately without the staff member logging in again.
     */
    @Transactional(readOnly = true)
    public boolean canAccess(String email, AdminSection section) {
        return repository.findByEmail(normalizeEmail(email)).map(u -> u.canAccess(section)).orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean isAdmin(String email) {
        return repository.findByEmail(normalizeEmail(email)).map(u -> u.getRole() == Role.ADMIN).orElse(false);
    }

    @Transactional(readOnly = true)
    public Page<AppUser> search(String q, Role role, Pageable pageable) {
        Specification<AppUser> spec = (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
                where.add(cb.or(cb.like(root.get("email"), like), cb.like(cb.lower(root.get("fullName")), like)));
            }
            if (role != null) {
                where.add(cb.equal(root.get("role"), role));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
        return repository.findAll(spec, pageable);
    }

    /**
     * Makes an account CUSTOMER or STAFF (with the given sections). ADMIN accounts come only from ADMIN_EMAILS
     * and cannot be changed here, so an admin can never lock themselves or another admin out.
     */
    @Transactional
    public AppUser updateAccess(Long userId, Role role, Set<AdminSection> permissions) {
        AppUser user = repository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản"));
        if (user.getRole() == Role.ADMIN || adminEmails.contains(user.getEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Không thể thay đổi quyền của quản trị viên (được cấu hình trong ADMIN_EMAILS)");
        }
        if (role == Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Quyền quản trị viên chỉ được cấp qua cấu hình ADMIN_EMAILS");
        }
        user.setAccess(role, permissions == null ? Set.of() : permissions);
        return user;
    }

    public boolean isConfiguredAdmin(AppUser user) {
        return adminEmails.contains(user.getEmail());
    }

    static class EmailTakenException extends ResponseStatusException {
        EmailTakenException() {
            super(HttpStatus.CONFLICT, "Email này đã được đăng ký");
        }
    }
}
