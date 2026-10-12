package com.florin.bank.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Runs after authentication so signed-in callers are limited per customer and anonymous ones per
 * address. Sign-in and sign-up are always limited per address to slow credential guessing; money
 * movement and account-number lookups have their own, tighter buckets.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiter limiter;
    private final RateLimitProperties properties;

    public RateLimitFilter(RateLimiter limiter, RateLimitProperties properties) {
        this.limiter = limiter;
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.enabled() || "OPTIONS".equals(request.getMethod())
                || !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String method = request.getMethod();
        String path = request.getRequestURI();
        String caller = caller(request);
        String name;
        RateLimitProperties.Rule rule;
        if ("POST".equals(method) && (path.equals("/api/auth/login") || path.equals("/api/customers/register"))) {
            name = "auth";
            rule = properties.auth();
            caller = "ip:" + request.getRemoteAddr();
        } else if ("POST".equals(method) && (path.equals("/api/transfers")
                || path.endsWith("/deposits") || path.endsWith("/withdrawals"))) {
            name = "money";
            rule = properties.money();
        } else if ("POST".equals(method) && path.startsWith("/api/payees")) {
            // Lookups and new payees both resolve account numbers to names: limit how fast anyone can probe them.
            name = "lookup";
            rule = properties.lookup();
        } else if ("GET".equals(method)) {
            name = "read";
            rule = properties.read();
        } else {
            name = "write";
            rule = properties.write();
        }

        RateLimiter.Decision decision = limiter.tryAcquire("florin:ratelimit:" + name + ":" + caller, rule);
        response.setHeader("X-RateLimit-Limit", Integer.toString(rule.capacity()));
        response.setHeader("X-RateLimit-Remaining", Long.toString(decision.remaining()));
        if (!decision.allowed()) {
            long seconds = Math.max(1, (decision.retryAfterMillis() + 999) / 1000);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", Long.toString(seconds));
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Too Many Requests\",\"status\":429,"
                    + "\"detail\":\"Too many requests. Try again in " + seconds + " seconds.\",\"code\":\"RATE_LIMITED\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static String caller(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Long customerId) {
            return "customer:" + customerId;
        }
        return "ip:" + request.getRemoteAddr();
    }
}
