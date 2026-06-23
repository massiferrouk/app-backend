package com.studup.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Configure WebSocket avec STOMP.
 * STOMP ajoute la notion de topics (canaux) par-dessus WebSocket brut.
 * Flutter s'abonne à /topic/conversation/{id} pour recevoir les messages en temps réel.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Préfixe des topics auxquels les clients s'abonnent pour recevoir des messages
        registry.enableSimpleBroker("/topic");

        // Préfixe des destinations que les clients utilisent pour envoyer des messages
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Point d'entrée WebSocket : ws://host/ws
        // withSockJS() = fallback automatique si WebSocket bloqué par un proxy (Railway)
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }
}
