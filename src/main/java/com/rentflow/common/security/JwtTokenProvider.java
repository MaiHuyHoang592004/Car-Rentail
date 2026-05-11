package com.rentflow.common.security;

import com.rentflow.auth.entity.Role;
import com.rentflow.common.exception.AuthenticationException;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class JwtTokenProvider {

    private static final String CLAIM_USER_ID = "uid";
    private static final String CLAIM_EMAIL = "em";
    private static final String CLAIM_ROLES = "rl";

    private final SecretKey secretKey;
    private final Duration accessTokenExpiry;
    private final Duration refreshTokenExpiry;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiry:PT15M}") Duration accessTokenExpiry,
            @Value("${jwt.refresh-token-expiry:P7D}") Duration refreshTokenExpiry) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiry = accessTokenExpiry;
        this.refreshTokenExpiry = refreshTokenExpiry;
    }

    public String generateAccessToken(UUID userId, String email, List<Role> roles) {
        Instant now = Instant.now();
        Instant expiry = now.plus(accessTokenExpiry);

        List<String> roleNames = roles.stream().map(Role::name).toList();

        return Jwts.builder()
                .subject(userId.toString())
                .claim(CLAIM_USER_ID, userId.toString())
                .claim(CLAIM_EMAIL, email)
                .claim(CLAIM_ROLES, roleNames)
                .issuedAt(java.util.Date.from(now))
                .expiration(java.util.Date.from(expiry))
                .signWith(secretKey, Jwts.SIG.HS512)
                .compact();
    }

    public String generateRefreshToken(UUID userId) {
        Instant now = Instant.now();
        Instant expiry = now.plus(refreshTokenExpiry);

        return Jwts.builder()
                .subject(userId.toString())
                .claim("typ", "refresh")
                .issuedAt(java.util.Date.from(now))
                .expiration(java.util.Date.from(expiry))
                .signWith(secretKey, Jwts.SIG.HS512)
                .compact();
    }

    public JwtClaims validateAccessToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String userIdStr = claims.get(CLAIM_USER_ID, String.class);
            if (userIdStr == null) {
                userIdStr = claims.getSubject();
            }
            String email = claims.get(CLAIM_EMAIL, String.class);

            @SuppressWarnings("unchecked")
            List<String> roleNames = claims.get(CLAIM_ROLES, List.class);
            List<Role> roles = roleNames != null
                    ? roleNames.stream().map(Role::valueOf).toList()
                    : List.of();

            return new JwtClaims(UUID.fromString(userIdStr), email, roles);

        } catch (ExpiredJwtException e) {
            throw AuthenticationException.tokenExpired();
        } catch (SignatureException | MalformedJwtException | IllegalArgumentException e) {
            throw AuthenticationException.invalidToken();
        } catch (JwtException e) {
            throw AuthenticationException.invalidToken();
        }
    }

    public Instant getAccessTokenExpiry() {
        return Instant.now().plus(accessTokenExpiry);
    }

    public Instant getRefreshTokenExpiry() {
        return Instant.now().plus(refreshTokenExpiry);
    }

    public record JwtClaims(UUID userId, String email, List<Role> roles) {}
}
