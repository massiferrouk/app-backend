package com.yuniv.backend.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // API REST stateless : pas de session, pas de CSRF
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // CORS géré par WebMvcConfig
            .cors(cors -> {})
            .authorizeHttpRequests(auth -> auth
                // Endpoints publics : inscription et confirmation email
                .requestMatchers(
                    "/api/v1/auth/register",
                    "/api/v1/auth/confirm",
                    "/actuator/health/**"
                ).permitAll()
                // Tout le reste nécessite une authentification
                .anyRequest().authenticated()
            )
            // Pas de formulaire de login HTML — on est une API REST
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
