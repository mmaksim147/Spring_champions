
package org.example.apigateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    @Value("${jwt.secret}")
    private String secret;

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/auth/login",
            "/api/auth/register",
            "/auth/login",
            "/auth/register",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/webjars/**",
            "/v3/api-docs",
            "/v3/api-docs/**",
            "/v3/api-docs/auth",
            "/v3/api-docs/product",
            "/v3/api-docs/compatibility",
            "/v3/api-docs/order",
            "/actuator/**",
            "/fallback/**"
    );

    private static final List<String> PUBLIC_GET_PATHS = List.of(
            "/api/components",
            "/api/components/**",
            "/api/categories",
            "/api/categories/**",
            "/components",
            "/components/**",
            "/categories",
            "/categories/**"
    );

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(
                secret.getBytes(StandardCharsets.UTF_8)
        );
    }

    private boolean matchesAny(List<String> patterns, String path) {
        return patterns.stream()
                .anyMatch(pattern -> MATCHER.match(pattern, path));
    }

    @Override
    public Mono<Void> filter(
            ServerWebExchange exchange,
            GatewayFilterChain chain
    ) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        ServerHttpRequest cleanRequest = request.mutate()
                .headers(headers -> {
                    headers.remove("X-User-Id");
                    headers.remove("X-User-Name");
                    headers.remove("X-User-Roles");
                })
                .build();

        ServerWebExchange cleanExchange = exchange.mutate()
                .request(cleanRequest)
                .build();

        boolean publicPath = matchesAny(PUBLIC_PATHS, path);

        boolean publicCatalogGet =
                request.getMethod() == HttpMethod.GET
                        && matchesAny(PUBLIC_GET_PATHS, path);

        if (publicPath || publicCatalogGet) {
            return chain.filter(cleanExchange);
        }

        String authHeader = cleanRequest.getHeaders()
                .getFirst("Authorization");

        if (authHeader == null
                || !authHeader.startsWith("Bearer ")) {
            return onError(
                    cleanExchange,
                    "Missing or invalid Authorization header",
                    HttpStatus.UNAUTHORIZED
            );
        }

        String token = authHeader.substring(7);

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String username = claims.getSubject();
            Number userIdNumber = claims.get("userId", Number.class);
            List<?> roles = claims.get("roles", List.class);

            if (username == null || username.isBlank()
                    || userIdNumber == null
                    || userIdNumber.longValue() <= 0) {
                return onError(
                        cleanExchange,
                        "Invalid JWT claims",
                        HttpStatus.UNAUTHORIZED
                );
            }

            String rolesString = roles != null
                    ? roles.stream()
                    .map(Object::toString)
                    .collect(Collectors.joining(","))
                    : "";

            ServerHttpRequest authenticatedRequest =
                    cleanRequest.mutate()
                            .headers(headers -> {
                                headers.set(
                                        "X-User-Id",
                                        userIdNumber.toString()
                                );
                                headers.set(
                                        "X-User-Name",
                                        username
                                );
                                headers.set(
                                        "X-User-Roles",
                                        rolesString
                                );
                            })
                            .build();

            return chain.filter(
                    cleanExchange.mutate()
                            .request(authenticatedRequest)
                            .build()
            );

        } catch (Exception e) {
            return onError(
                    cleanExchange,
                    "Invalid or expired JWT token",
                    HttpStatus.UNAUTHORIZED
            );
        }
    }

    private Mono<Void> onError(
            ServerWebExchange exchange,
            String message,
            HttpStatus status
    ) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders()
                .set("Content-Type", "application/json");

        String body = "{\"error\":\"" + message + "\"}";

        return exchange.getResponse().writeWith(
                Mono.just(
                        exchange.getResponse()
                                .bufferFactory()
                                .wrap(body.getBytes(StandardCharsets.UTF_8))
                )
        );
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
