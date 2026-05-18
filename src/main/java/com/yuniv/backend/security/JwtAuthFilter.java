package com.yuniv.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filtre JWT : intercepte chaque requête HTTP et authentifie l'utilisateur
 * si un token valide est présent dans le header Authorization.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final CustomUserDetailsService userDetailsService;

    public JwtAuthFilter(JwtUtil jwtUtil, CustomUserDetailsService userDetailsService) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        // Étape 1 : lire le header Authorization
        String authHeader = request.getHeader("Authorization");

        // Si le header est absent ou ne commence pas par "Bearer ", on laisse passer sans authentifier
        // Spring Security refusera l'accès plus loin si la route est protégée
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Étape 2 : extraire le token (tout ce qui suit "Bearer ")
        String token = authHeader.substring(7);

        // Étape 3 : valider le token et authentifier seulement si pas déjà authentifié
        if (jwtUtil.isTokenValid(token) && SecurityContextHolder.getContext().getAuthentication() == null) {

            // Charger l'utilisateur depuis la BDD à partir de son userId dans le token
            String email = jwtUtil.extractClaims(token).get("email", String.class);
            UserDetails userDetails = userDetailsService.loadUserByUsername(email);

            // Étape 4 : créer l'objet d'authentification et l'enregistrer dans le SecurityContext
            // UsernamePasswordAuthenticationToken(principal, credentials, authorities)
            // credentials = null car on n'a plus besoin du mot de passe à ce stade
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    userDetails,
                    null,
                    userDetails.getAuthorities()
            );

            // Ajoute des détails sur la requête (IP, session) — utile pour les logs de sécurité
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            // Enregistre l'authentification : à partir d'ici, Spring Security sait qui fait la requête
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        // On passe à la suite de la chaîne de filtres (puis au controller)
        filterChain.doFilter(request, response);
    }
}
