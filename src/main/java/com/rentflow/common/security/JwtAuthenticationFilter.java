package com.rentflow.common.security;

import com.rentflow.auth.entity.UserStatus;
import com.rentflow.auth.repository.AuthUserRepository;
import com.rentflow.common.exception.AuthenticationException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;
    private final JwtAuthenticationEntryPoint entryPoint;
    private final AuthUserRepository authUserRepository;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider,
                                  JwtAuthenticationEntryPoint entryPoint,
                                  AuthUserRepository authUserRepository) {
        this.tokenProvider = tokenProvider;
        this.entryPoint = entryPoint;
        this.authUserRepository = authUserRepository;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String token = extractToken(request);

        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        JwtTokenProvider.JwtClaims claims;
        try {
            claims = tokenProvider.validateAccessToken(token);
        } catch (org.springframework.security.core.AuthenticationException e) {
            SecurityContextHolder.clearContext();
            entryPoint.commence(request, response, e);
            return;
        } catch (Exception e) {
            SecurityContextHolder.clearContext();
            entryPoint.commence(request, response, null);
            return;
        }

        // I19: reject tokens belonging to non-ACTIVE users (e.g. suspended after issue).
        // Missing user falls through to the role-based access checks downstream.
        Optional<UserStatus> status = authUserRepository.findStatusById(claims.userId());
        if (status.isPresent() && status.get() != UserStatus.ACTIVE) {
            SecurityContextHolder.clearContext();
            entryPoint.commence(request, response,
                    new AuthenticationException("AUTH_ACCOUNT_SUSPENDED", "Account is suspended"));
            return;
        }

        UserPrincipal principal = new UserPrincipal(
                claims.userId(),
                claims.email(),
                claims.roles()
        );

        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities()
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals("/api/v1/auth/register")
                || path.equals("/api/v1/auth/login")
                || path.equals("/api/v1/auth/refresh")
                || path.equals("/api/v1/auth/logout")
                || path.equals("/api/v1/auth/forgot-password")
                || path.equals("/api/v1/auth/reset-password")
                || path.equals("/api/v1/auth/verify-email")
                || path.equals("/api/v1/health")
                || path.equals("/actuator/health")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/api-docs")
                || path.startsWith("/v3/api-docs");
    }
}
