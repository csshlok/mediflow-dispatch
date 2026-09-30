package org.example.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtValidator {

    private final JwtParser parser;

    // Built once at startup, so a missing, malformed or too-short secret fails fast
    public JwtValidator(@Value("${jwt.secret}") String secretKey,
                        @Value("${medical.security.jwt.issuer}") String issuer) {
        this.parser = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretKey)))
                .requireIssuer(issuer)
                .build();
    }

    // Validates signature, expiry and issuer; throws if the token is tampered, expired or foreign
    public Claims extractAllClaims(String token) {
        return parser.parseSignedClaims(token).getPayload();
    }
}
