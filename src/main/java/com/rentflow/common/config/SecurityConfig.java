package com.rentflow.common.config;

import com.rentflow.common.security.JwtAuthenticationEntryPoint;
import com.rentflow.common.security.JwtAuthenticationFilter;
import com.rentflow.common.security.JsonAccessDeniedHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JsonAccessDeniedHandler accessDeniedHandler;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint,
                          JsonAccessDeniedHandler accessDeniedHandler) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.jwtAuthenticationEntryPoint = jwtAuthenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex
                    .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler)
            )
            .authorizeHttpRequests(auth -> auth
                    // Public endpoints
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/register").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/refresh").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/logout").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/health").permitAll()
                    .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                    // Swagger / OpenAPI
                    .requestMatchers("/swagger-ui/**").permitAll()
                    .requestMatchers("/swagger-ui.html").permitAll()
                    .requestMatchers("/api-docs/**").permitAll()
                    .requestMatchers("/v3/api-docs/**").permitAll()
                    // Public listing endpoints
                    .requestMatchers(HttpMethod.GET, "/api/v1/listings").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/listings/{id}").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/listings/{id}/availability").permitAll()
                    // Booking endpoints require authentication; service layer enforces roles and ownership
                    .requestMatchers(HttpMethod.POST, "/api/v1/bookings").authenticated()
                    .requestMatchers(HttpMethod.GET, "/api/v1/bookings/me").authenticated()
                    .requestMatchers(HttpMethod.GET, "/api/v1/bookings/{id}").authenticated()
                    .requestMatchers(HttpMethod.PATCH, "/api/v1/bookings/{id}").authenticated()
                    // Admin endpoints require ADMIN
                    .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                    .requestMatchers("/api/v1/host/**").hasRole("HOST")
                    // All other endpoints require authentication
                    .anyRequest().authenticated()
            );

        // Add JWT filter BEFORE AuthorizationFilter so token validation happens before auth check
        http.addFilterBefore(jwtAuthenticationFilter, AuthorizationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
