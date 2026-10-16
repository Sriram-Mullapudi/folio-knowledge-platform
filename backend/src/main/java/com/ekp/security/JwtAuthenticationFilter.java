package com.ekp.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Validates the {@code Authorization: Bearer <jwt>} header on each request. On a
 * valid token it populates the {@link SecurityContextHolder} with an
 * {@link AuthenticatedUser} principal and a {@code ROLE_*} authority derived
 * straight from the signed claims — no database lookup on the hot path.
 *
 * <p>If no/invalid token is present the filter simply does nothing; downstream
 * authorization rules then reject protected endpoints with 401/403.
 *
 * <p>Deliberately NOT a {@code @Component}: it is instantiated in
 * {@code SecurityConfig} and inserted into the security filter chain only, which
 * avoids Spring Boot also auto-registering it as a global servlet filter.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {

            String token = header.substring(BEARER_PREFIX.length());
            jwtService.parse(token).ifPresent(parsed -> {
                AuthenticatedUser principal =
                        new AuthenticatedUser(parsed.userId(), parsed.email(), parsed.role());
                var authority = new SimpleGrantedAuthority("ROLE_" + parsed.role());
                var authentication = new UsernamePasswordAuthenticationToken(
                        principal, null, List.of(authority));
                authentication.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            });
        }

        filterChain.doFilter(request, response);
    }
}
