package com.im.platform.session.manager;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Spring Boot 自动配置的 RedisTemplate 是 RedisTemplate&lt;Object, Object&gt;,
 * 泛型和 SessionManager 需要的 RedisTemplate&lt;String, SessionRecord&gt; 不匹配,
 * 自动装配会失败,必须显式声明一个精确泛型的 bean。
 */
@Configuration
public class SessionRedisConfig {

    @Bean
    public RedisTemplate<String, SessionRecord> sessionRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, SessionRecord> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }
}
