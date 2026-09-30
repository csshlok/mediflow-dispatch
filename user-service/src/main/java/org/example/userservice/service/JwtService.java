package org.example.userservice.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.example.userservice.entity.User;
import org.example.userservice.enums.Role;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final JwtParser parser;
    private final long jwtExpiration;
    private final String issuer;

    // The key is built once at startup, so a missing, malformed or too-short secret fails fast
    public JwtService(@Value("${jwt.secret}") String secretKey,
                      @Value("${jwt.expiration}") long jwtExpiration,
                      @Value("${medical.security.jwt.issuer}") String issuer) {
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretKey));
        this.jwtExpiration = jwtExpiration;
        this.issuer = issuer;
        this.parser = Jwts.parser().verifyWith(signingKey).requireIssuer(issuer).build();
    }

    // Generates the token using the user's ID and Role
    public String generateToken(User user) {
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("role", user.getRole().name());
        if (user.getAmbulanceId() != null) {
            // The gateway only lets this paramedic act on this ambulance
            extraClaims.put("ambulanceId", user.getAmbulanceId().toString());
        }

        long now = System.currentTimeMillis();
        return Jwts.builder()
                .claims(extraClaims)
                .issuer(issuer)
                .subject(user.getId().toString())
                .issuedAt(new Date(now))
                .expiration(new Date(now + jwtExpiration))
                .signWith(signingKey)
                .compact();
    }

    // Validates signature, expiry and issuer, then returns the role claim
    public Role extractRole(String token) {
        Claims claims = parser.parseSignedClaims(token).getPayload();
        return Role.valueOf(claims.get("role", String.class));
    }
}
