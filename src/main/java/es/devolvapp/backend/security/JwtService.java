package es.devolvapp.backend.security;

import es.devolvapp.backend.entity.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;

@Service
public class JwtService {
    private final SecretKey key;
    private final long accessTtlSeconds;

    public JwtService(@Value("$" + "{app.jwt.secret-base64}") String secret,
                      @Value("$" + "{app.jwt.access-ttl-seconds:900}") long accessTtlSeconds) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.accessTtlSeconds = accessTtlSeconds;
    }

    public String createAccessToken(AuthUser user) {
        Instant now = Instant.now();
        List<String> roles = user.roles().stream().map(Role::name).toList();
        return Jwts.builder().subject(user.id().toString()).claim("email", user.email())
                .claim("roles", roles).issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(accessTtlSeconds))).signWith(key).compact();
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    public long getAccessTtlSeconds() { return accessTtlSeconds; }
}
