package com.ekp.auth;

import com.ekp.audit.AuditAction;
import com.ekp.audit.AuditService;
import com.ekp.auth.dto.AuthResponse;
import com.ekp.auth.dto.LoginRequest;
import com.ekp.auth.dto.RegisterRequest;
import com.ekp.auth.dto.UserResponse;
import com.ekp.common.error.ConflictException;
import com.ekp.common.error.InvalidRefreshTokenException;
import com.ekp.common.error.ResourceNotFoundException;
import com.ekp.security.AppUserPrincipal;
import com.ekp.security.JwtService;
import com.ekp.user.Role;
import com.ekp.user.User;
import com.ekp.user.UserRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final AuthenticationManager authenticationManager;
    private final AuditService auditService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       RefreshTokenService refreshTokenService,
                       AuthenticationManager authenticationManager,
                       AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.authenticationManager = authenticationManager;
        this.auditService = auditService;
    }

    /**
     * Self-serve registration. Always assigns {@link Role#USER}; a client can
     * never escalate itself to ADMIN. Auto-issues a session on success.
     */
    @Transactional
    public AuthResponse register(RegisterRequest request, String ip) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("Email already registered");
        }
        User user = new User(email, passwordEncoder.encode(request.password()), Role.USER);
        userRepository.save(user);
        auditService.record(user.getId(), AuditAction.REGISTER, null, user.getId(),
                Map.of("email", email), ip);
        return issueSession(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request, String ip) {
        String email = request.email().trim().toLowerCase();
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.password()));
            User user = ((AppUserPrincipal) authentication.getPrincipal()).getUser();
            auditService.record(user.getId(), AuditAction.LOGIN, ip);
            return issueSession(user);
        } catch (AuthenticationException ex) {
            // Audit the failure without leaking whether the email exists.
            auditService.record(null, AuditAction.LOGIN_FAILED, null, null,
                    Map.of("email", email), ip);
            throw ex;
        }
    }

    @Transactional
    public AuthResponse refresh(String refreshToken) {
        RefreshTokenService.Rotation rotation = refreshTokenService.rotate(refreshToken)
                .orElseThrow(InvalidRefreshTokenException::new);
        User user = userRepository.findById(rotation.userId())
                .orElseThrow(() -> new AuthenticationServiceException("User no longer exists"));
        String accessToken = jwtService.generateAccessToken(user);
        return AuthResponse.bearer(accessToken, rotation.newRefreshToken(),
                jwtService.getAccessTtlSeconds(), UserResponse.from(user));
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    @Transactional(readOnly = true)
    public UserResponse currentUser(UUID userId) {
        return userRepository.findById(userId)
                .map(UserResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    private AuthResponse issueSession(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = refreshTokenService.issue(user.getId());
        return AuthResponse.bearer(accessToken, refreshToken,
                jwtService.getAccessTtlSeconds(), UserResponse.from(user));
    }
}
