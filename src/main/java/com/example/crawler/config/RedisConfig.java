package com.example.crawler.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis配置类
 * 
 * <p>配置RedisTemplate，使用String序列化器，确保与Worker服务兼容。
 * </p>
 */
@Configuration
public class RedisConfig {

    /**
     * 配置RedisTemplate
     * 
     * <p>使用StringRedisSerializer进行序列化，确保：
     * <ul>
     *   <li>键和值都以String形式存储</li>
     *   <li>与外部Worker服务兼容</li>
     *   <li>便于调试和查看Redis中的数据</li>
     * </ul>
     * </p>
     * 
     * @param connectionFactory Redis连接工厂
     * @return 配置好的RedisTemplate
     */
    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        // 使用String序列化器
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());
        
        template.afterPropertiesSet();
        return template;
    }
}

