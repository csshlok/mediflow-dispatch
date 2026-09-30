package org.example.gateway.filter;

import org.example.gateway.security.JwtValidator;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class JwtAuthenticationFilter extends AbstractGatewayFilterFactory<JwtAuthenticationFilter.Config> {

    private final JwtValidator jwtValidator;

    public JwtAuthenticationFilter(JwtValidator jwtValidator) {
        super(Config.class);
        this.jwtValidator = jwtValidator;
    }

    public static class Config {
        // Configuration properties can go here if needed
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {

            // 1. Extract the Authorization Header
            String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

            // 2. Check if it's missing (null) OR incorrectly formatted
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return onError(exchange, "Missing or Invalid Authorization Header", HttpStatus.UNAUTHORIZED);
            }

            // 2. Extract and Validate Token
            String token = authHeader.substring(7);
            String userRole;
            try {
                // If this doesn't crash, the token signature and expiration are completely valid
                userRole = jwtValidator.extractRole(token);
            } catch (Exception e) {
                return onError(exchange, "Invalid or Expired JWT Token", HttpStatus.UNAUTHORIZED);
            }

            // 3. Enforce the Authorization Matrix (RBAC)
            String path = exchange.getRequest().getURI().getPath();
            String method = exchange.getRequest().getMethod().name();

            if (!isAuthorized(userRole, path, method)) {
                return onError(exchange, "Insufficient permissions for this action", HttpStatus.FORBIDDEN);
            }

            // 4. Everything is good! Forward the request to the business service.
            return chain.filter(exchange);
        };
    }

    // Role-Based Access Control Logic
    private boolean isAuthorized(String role, String path, String method) {
        if ("ADMIN".equals(role)) {
            return true; // Admins can do everything
        }

        if ("DISPATCHER".equals(role)) {
            return path.startsWith("/api/emergency") && method.equals("POST");
        }

        if ("PARAMEDIC".equals(role)) {
            // Paramedics can only update ambulance statuses
            return path.startsWith("/api/ambulances/") && method.equals("POST");
        }

        return false;
    }

    // Helper method to return custom errors cleanly
    private Mono<Void> onError(ServerWebExchange exchange, String err, HttpStatus httpStatus) {
        exchange.getResponse().setStatusCode(httpStatus);
        System.err.println("🚨 Gateway Blocked Request: " + err);
        return exchange.getResponse().setComplete();
    }
}
