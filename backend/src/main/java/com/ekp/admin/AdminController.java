package com.ekp.admin;

import com.ekp.auth.dto.UserResponse;
import com.ekp.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Admin-only endpoints. Protected twice over: by the URL rule in SecurityConfig
 * ({@code /api/v1/admin/** -> hasRole(ADMIN)}) and by method security here. The
 * full admin surface (user management, ingestion status, feedback) is fleshed
 * out in Phase 8; this slice exists to prove RBAC works end to end.
 */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin", description = "Administrative operations (ADMIN role required)")
public class AdminController {

    private final UserRepository userRepository;

    public AdminController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List all users", security = @SecurityRequirement(name = "bearer"))
    public List<UserResponse> listUsers() {
        return userRepository.findAll().stream()
                .map(UserResponse::from)
                .toList();
    }
}
