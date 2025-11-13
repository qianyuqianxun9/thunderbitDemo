package com.example.crawler.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka配置类
 * 
 * <p>配置Kafka主题，确保主题在应用启动时自动创建。
 * </p>
 */
@Configuration
public class KafkaConfig {

    /**
     * 创建爬虫任务主题
     * 
     * <p>如果主题不存在，Kafka会自动创建。
     * 配置：
     * - 分区数：3（支持并行处理）
     * - 副本数：1（单机环境，生产环境建议3）
     * </p>
     * 
     * @return Kafka主题配置
     */
    @Bean
    public NewTopic crawlerJobTopic() {
        return TopicBuilder.name(KafkaConstants.JOB_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
}

