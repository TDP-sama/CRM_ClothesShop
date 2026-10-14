package com.example.shop.auth;

import com.example.shop.common.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Account roles and staff permissions. Only ADMIN reaches these endpoints (see SecurityConfig). */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    record AccessUpdate(@NotNull(message = "Vui lòng chọn vai trò") Role role, Set<AdminSection> permissions) {
    }

    /** {@code locked}: an ADMIN from ADMIN_EMAILS, whose access cannot be edited here. */
    record UserRow(Long id, String email, String fullName, Role role, Set<AdminSection> permissions,
            boolean locked, Instant createdAt) {
    }

    private final UserService users;

    public AdminUserController(UserService users) {
        this.users = users;
    }

    @GetMapping
    public PageResponse<UserRow> list(@RequestParam(required = false) String q,
            @RequestParam(required = false) Role role,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100),
                Sort.by("createdAt").descending().and(Sort.by("id").descending()));
        return PageResponse.of(users.search(q, role, pageRequest), this::toRow);
    }

    @PutMapping("/{id}/access")
    public UserRow updateAccess(@PathVariable Long id, @Valid @RequestBody AccessUpdate request) {
        return toRow(users.updateAccess(id, request.role(), request.permissions()));
    }

    private UserRow toRow(AppUser u) {
        return new UserRow(u.getId(), u.getEmail(), u.getDisplayName(), u.getRole(), u.getEffectivePermissions(),
                u.getRole() == Role.ADMIN || users.isConfiguredAdmin(u), u.getCreatedAt());
    }
}
