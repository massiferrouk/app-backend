package com.studup.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Configuration du client Redis.
 *
 * Spring Boot auto-configure RedisTemplate<Object, Object> mais pas RedisTemplate<String, Object>.
 * On définit ce bean explicitement pour qu'il soit injecté dans les services qui en ont besoin
 * (CalendrierService pour le cache matching, JwtBlacklistService pour la blacklist JWT).
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Clés stockées en String lisible (ex: "matching:uuid1:uuid2")
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());

        // Valeurs sérialisées en JSON pour faciliter le debug et la portabilité
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());

        return template;
    }
}
