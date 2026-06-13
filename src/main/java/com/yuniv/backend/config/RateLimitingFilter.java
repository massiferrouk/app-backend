package com.yuniv.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuniv.backend.exception.ErrorResponse;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;

    // ConcurrentHashMap = thread-safe : plusieurs requêtes peuvent arriver en même temps
    // Clé = "IP:endpoint" → chaque IP a un bucket séparé pour /login et pour /register
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimitingFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String path = request.getRequestURI();

        // On applique le rate limiting uniquement sur ces deux endpoints
        if (!path.equals("/api/v1/auth/login") && !path.equals("/api/v1/auth/register")) {
            filterChain.doFilter(request, response);
            return;
        }

        String ip = getClientIp(request);
        // Clé unique = IP + endpoint (ex: "192.168.1.1:/api/v1/auth/login")
        String bucketKey = ip + ":" + path;

        // computeIfAbsent = crée le bucket si cette IP n'en a pas encore,
        // sinon retourne celui qui existe déjà
        Bucket bucket = buckets.computeIfAbsent(bucketKey, k -> createBucket(path));

        if (bucket.tryConsume(1)) {
            // Il reste des jetons → on laisse passer la requête
            filterChain.doFilter(request, response);
        } else {
            // Plus de jetons → HTTP 429 Too Many Requests
            sendRateLimitResponse(response, path);
        }
    }

    // Crée un bucket avec la limite adaptée à l'endpoint
    private Bucket createBucket(String path) {
        Bandwidth limit = switch (path) {
            // /login : 5 requêtes par minute par IP
            case "/api/v1/auth/login" ->
                    Bandwidth.builder()
                            .capacity(5)
                            .refillIntervally(5, Duration.ofMinutes(1))
                            .build();
            // /register : 3 requêtes par minute par IP
            case "/api/v1/auth/register" ->
                    Bandwidth.builder()
                            .capacity(3)
                            .refillIntervally(3, Duration.ofMinutes(1))
                            .build();
            default ->
                    Bandwidth.builder()
                            .capacity(60)
                            .refillIntervally(60, Duration.ofMinutes(1))
                            .build();
        };

        return Bucket.builder().addLimit(limit).build();
    }

    // Vide tous les buckets — utilisé dans les tests pour repartir d'un état propre
    public void resetBuckets() {
        buckets.clear();
    }

    // Récupère l'IP réelle du client
    // X-Forwarded-For = header ajouté par les proxys/load balancers (Railway, Nginx...)
    // Sans ce header, on prendrait toujours l'IP du proxy, pas du vrai client
    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // X-Forwarded-For peut contenir plusieurs IPs séparées par virgule :
            // "client, proxy1, proxy2" → on prend toujours la première (le vrai client)
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    // Construit la réponse HTTP 429 au même format que notre GlobalExceptionHandler
    private void sendRateLimitResponse(HttpServletResponse response, String path) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        // Standard HTTP RFC 6585 : indique au client combien de secondes attendre avant de réessayer
        response.setHeader("Retry-After", "60");

        ErrorResponse errorResponse = new ErrorResponse(
                "RATE_LIMIT_EXCEEDED",
                "Trop de tentatives. Réessayez dans 1 minute.",
                OffsetDateTime.now(),
                path,
                java.util.List.of()
        );

        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
}