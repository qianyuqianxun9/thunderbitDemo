package com.example.crawler.config;

/**
 * Kafka主题常量定义
 * 
 * <p>统一管理Kafka中使用的所有主题名称，避免硬编码。
 * </p>
 */
public final class KafkaConstants {

    private KafkaConstants() {
        // 工具类，禁止实例化
    }

    /**
     * 爬虫任务队列主题名称
     * 
     * <p>用于存储待处理的爬虫任务。
     * Producer将任务消息发送到此主题，Consumer从此主题消费任务。
     * </p>
     */
    public static final String JOB_TOPIC = "crawler-job-topic";
}

