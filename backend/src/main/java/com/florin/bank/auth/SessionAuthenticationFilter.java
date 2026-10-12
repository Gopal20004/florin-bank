package com.florin.bank.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/** Turns a valid "Authorization: Bearer" token into an authentication whose principal is the customer id. */
public class SessionAuthenticationFilter extends OncePerRequestFilter {

    private final AuthService authService;

    public SessionAuthenticationFilter(AuthService authService) {
        this.authService = authService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = BearerToken.from(request.getHeader("Authorization"));
        if (token != null) {
            authService.authenticate(token).ifPresent(customerId -> {
                var authentication = new UsernamePasswordAuthenticationToken(customerId, null, List.of());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            });
        }
        filterChain.doFilter(request, response);
    }
}
