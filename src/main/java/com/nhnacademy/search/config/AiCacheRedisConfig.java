package com.nhnacademy.search.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.search.dto.BookSearchResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class AiCacheRedisConfig {

    @Bean(name = "aiSearchRedisTemplate")
    public RedisTemplate<String, BookSearchResponse> aiSearchRedisTemplate(
            RedisConnectionFactory cf,
            ObjectMapper objectMapper
    ) {
        RedisTemplate<String, BookSearchResponse> template = new RedisTemplate<>();
        template.setConnectionFactory(cf);

        StringRedisSerializer keySer = new StringRedisSerializer();

        Jackson2JsonRedisSerializer<BookSearchResponse> valueSer =
                new Jackson2JsonRedisSerializer<>(objectMapper, BookSearchResponse.class);

        template.setKeySerializer(keySer);
        template.setValueSerializer(valueSer);
        template.setHashKeySerializer(keySer);
        template.setHashValueSerializer(valueSer);

        template.afterPropertiesSet();
        return template;
    }
}
