package id.ac.ui.cs.advprog.order.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JwtServiceTest {

    @Test
    void parseTokenShouldReadClaimsUsingBase64Secret() {
        String secret = "c29tZS1iYXNlNjQtc2VjcmV0LXN0cmluZy1mb3ItdGVzdGluZw==";
        JwtService jwtService = new JwtService(secret);
        Claims claims = jwtService.parseToken(token(secret, "7", "TITIPER"));

        assertEquals("7", claims.getSubject());
        assertEquals("TITIPER", claims.get("role", String.class));
    }

    private static String token(String secret, String subject, String role) {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        return Jwts.builder()
                .subject(subject)
                .claim("role", role)
                .signWith(key)
                .compact();
    }
}
