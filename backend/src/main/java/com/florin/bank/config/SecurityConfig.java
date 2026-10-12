package com.florin.bank.config;

import com.florin.bank.auth.AuthService;
import com.florin.bank.auth.SessionAuthenticationFilter;
import com.florin.bank.ratelimit.RateLimitFilter;
import com.florin.bank.ratelimit.RateLimitProperties;
import com.florin.bank.ratelimit.RateLimiter;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, AuthService authService,
                                                   RateLimiter rateLimiter, RateLimitProperties rateLimits)
            throws Exception {
        http
                .cors(Customizer.withDefaults())
                // Bearer tokens travel in a header the browser never adds by itself, so there is no CSRF to defend.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(SecurityConfig::unauthorized))
                .authorizeHttpRequests(auth -> auth
                        // Tomcat re-dispatches sendError() to /error; refusing that dispatch would turn every 404 and
                        // 400 into an empty 401 (a bug Sloth hit; ErrorResponseTests guards it here).
                        .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.ASYNC).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/customers/register", "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/config", "/actuator/health", "/actuator/health/**").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(new SessionAuthenticationFilter(authService), UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new RateLimitFilter(rateLimiter, rateLimits), SessionAuthenticationFilter.class);

        return http.build();
    }

    /** A problem JSON body like every other error, written directly so no error dispatch is involved. */
    private static void unauthorized(HttpServletRequest request, HttpServletResponse response,
                                     AuthenticationException exception) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Unauthorized\",\"status\":401,"
                + "\"detail\":\"Sign in to continue.\",\"code\":\"UNAUTHENTICATED\"}");
    }
}
