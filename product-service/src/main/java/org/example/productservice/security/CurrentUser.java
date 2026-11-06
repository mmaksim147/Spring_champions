
package org.example.productservice.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

@Component
public class CurrentUser {

    private final SecretKey signingKey;

    public CurrentUser(@Value("${jwt.secret}") String secret) {
        this.signingKey = Keys.hmacShaKeyFor(
                secret.getBytes(StandardCharsets.UTF_8)
        );
    }

    public Identity require(String authorization) {
        if (authorization == null
                || !authorization.startsWith("Bearer ")) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Требуется авторизация"
            );
        }

        String token = authorization.substring(7).trim();

        if (token.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Требуется авторизация"
            );
        }

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            Number id = claims.get("userId", Number.class);
            String username = claims.getSubject();
            List<?> roles = claims.get("roles", List.class);

            if (id == null
                    || id.longValue() <= 0
                    || username == null
                    || username.isBlank()) {
                throw new IllegalArgumentException(
                        "Invalid JWT claims"
                );
            }

            boolean admin = roles != null
                    && roles.stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .anyMatch("ROLE_ADMIN"::equals);

            return new Identity(
                    id.longValue(),
                    admin
            );

        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Недействительный или просроченный JWT"
            );
        }
    }

    public void requireOwnerOrAdmin(
            Identity identity,
            Long ownerId
    ) {
        if (!identity.admin()
                && !Objects.equals(identity.userId(), ownerId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Нет доступа к чужой сборке"
            );
        }
    }

    public record Identity(
            Long userId,
            boolean admin
    ) {
    }
}
