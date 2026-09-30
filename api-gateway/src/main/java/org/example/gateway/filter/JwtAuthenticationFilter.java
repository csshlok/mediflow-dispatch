package org.example.gateway.filter;

import io.jsonwebtoken.Claims;
import org.example.gateway.security.AccessPolicy;
import org.example.gateway.security.JwtValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class JwtAuthenticationFilter extends AbstractGatewayFilterFactory<JwtAuthenticationFilter.Config> {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

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

            // 1. Extract the bearer token
            String token = extractToken(exchange);
            if (token == null) {
                return onError(exchange, "Missing or Invalid Authorization Header", HttpStatus.UNAUTHORIZED);
            }

            // 2. Validate signature, expiry and issuer
            Claims claims;
            try {
                claims = jwtValidator.extractAllClaims(token);
            } catch (Exception e) {
                return onError(exchange, "Invalid or Expired JWT Token", HttpStatus.UNAUTHORIZED);
            }

            // 3. Enforce the Authorization Matrix (RBAC)
            String path = exchange.getRequest().getURI().getPath();
            String method = exchange.getRequest().getMethod().name();
            String role = claims.get("role", String.class);
            String ambulanceId = claims.get("ambulanceId", String.class);

            if (!AccessPolicy.isAllowed(role, ambulanceId, method, path)) {
                return onError(exchange, "Insufficient permissions for this action", HttpStatus.FORBIDDEN);
            }

            // 4. Everything is good! Forward the request to the business service.
            return chain.filter(exchange);
        };
    }

    private static String extractToken(ServerWebExchange exchange) {
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        // Browsers cannot set headers on a WebSocket handshake, so the stream accepts ?access_token= instead
        if (exchange.getRequest().getURI().getPath().startsWith("/ws/")) {
            return exchange.getRequest().getQueryParams().getFirst("access_token");
        }
        return null;
    }

    // Helper method to return custom errors cleanly
    private Mono<Void> onError(ServerWebExchange exchange, String err, HttpStatus httpStatus) {
        exchange.getResponse().setStatusCode(httpStatus);
        log.debug("Gateway blocked {} {}: {}", exchange.getRequest().getMethod(), exchange.getRequest().getURI().getPath(), err);
        return exchange.getResponse().setComplete();
    }
}
