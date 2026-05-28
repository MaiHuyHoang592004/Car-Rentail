package com.rentflow.common.ratelimit;

import com.rentflow.common.exception.RateLimitExceededException;
import com.rentflow.common.security.ClientIpResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;
import java.util.regex.Pattern;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class PublicEndpointRateLimitFilter extends OncePerRequestFilter {

    private static final Pattern LISTING_DETAIL_PATH = Pattern.compile("^/api/v1/listings/[^/]+$");
    private static final Pattern LISTING_AVAILABILITY_PATH = Pattern.compile("^/api/v1/listings/[^/]+/availability$");

    private final RateLimitService rateLimitService;
    private final ClientIpResolver clientIpResolver;
    private final HandlerExceptionResolver handlerExceptionResolver;

    public PublicEndpointRateLimitFilter(
            RateLimitService rateLimitService,
            ClientIpResolver clientIpResolver,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver) {
        this.rateLimitService = rateLimitService;
        this.clientIpResolver = clientIpResolver;
        this.handlerExceptionResolver = handlerExceptionResolver;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        try {
            String clientIp = clientIpResolver.resolve(request);
            rateLimitService.consumePublicEndpoint(clientIp);
            filterChain.doFilter(request, response);
        } catch (RateLimitExceededException ex) {
            handlerExceptionResolver.resolveException(request, response, null, ex);
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = normalizePath(request);
        return !isRateLimitedPublicPath(path);
    }

    private boolean isRateLimitedPublicPath(String path) {
        if ("/api/v1/listings".equals(path)
                || "/api/v1/health".equals(path)
                || "/actuator/health".equals(path)) {
            return true;
        }
        return LISTING_DETAIL_PATH.matcher(path).matches()
                || LISTING_AVAILABILITY_PATH.matcher(path).matches();
    }

    private String normalizePath(HttpServletRequest request) {
        String contextPath = request.getContextPath();
        String uri = request.getRequestURI();
        String path = contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)
                ? uri.substring(contextPath.length())
                : uri;
        if (path.length() > 1 && path.endsWith("/")) {
            return path.substring(0, path.length() - 1);
        }
        return path;
    }
}
