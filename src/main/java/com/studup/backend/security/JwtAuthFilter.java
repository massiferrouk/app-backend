package com.studup.backend.security;

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
    private final JwtBlacklistService jwtBlacklistService;

    public JwtAuthFilter(JwtUtil jwtUtil,
                         CustomUserDetailsService userDetailsService,
                         JwtBlacklistService jwtBlacklistService) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
        this.jwtBlacklistService = jwtBlacklistService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        // Pas de header : on laisse passer sans authentifier. C'est Spring
        // Security, plus loin dans la chaîne, qui refusera si la route est
        // protégée — le filtre n'a pas à décider de l'autorisation.
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        // Deux révocations distinctes, et il faut les deux :
        // - isBlacklisted(jti)      → CE token précis a été révoqué (logout) ;
        // - isUserRevoked(userId)   → TOUS les tokens de l'utilisateur ont été
        //   révoqués (suspension / bannissement par un admin). Sans cette
        //   seconde vérification, un compte banni continuait d'être servi
        //   jusqu'à l'expiration de son access token.
        String jti = jwtUtil.extractJti(token);
        if (jwtUtil.isTokenValid(token)
                && !jwtBlacklistService.isBlacklisted(jti)
                && !jwtBlacklistService.isUserRevoked(jwtUtil.extractUserId(token))
                && SecurityContextHolder.getContext().getAuthentication() == null) {

            // On recharge l'utilisateur en base plutôt que de faire confiance
            // aux claims : un rôle modifié depuis l'émission du token doit
            // prendre effet immédiatement.
            String email = jwtUtil.extractClaims(token).get("email", String.class);
            UserDetails userDetails = userDetailsService.loadUserByUsername(email);

            // credentials = null : le mot de passe n'a plus à circuler ici
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    userDetails,
                    null,
                    userDetails.getAuthorities()
            );

            // IP et session — tracés dans les logs de sécurité
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }
}
