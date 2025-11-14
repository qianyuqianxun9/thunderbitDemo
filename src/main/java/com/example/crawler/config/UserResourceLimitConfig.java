package com.example.crawler.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 用户资源限制配置
 * 
 * <p>配置每个用户在指定时间段内的最大资源消耗限制。
 * </p>
 */
@Configuration
@ConfigurationProperties(prefix = "crawler.user-resource-limit")
public class UserResourceLimitConfig {

    /**
     * 时间窗口（秒），默认1小时
     */
    private int timeWindowSeconds = 3600;

    /**
     * 每个用户在时间窗口内的最大线程数消耗
     */
    private int maxThreadsPerWindow = 50;

    /**
     * 每个用户在时间窗口内的最大任务数
     */
    private int maxJobsPerWindow = 10;

    public int getTimeWindowSeconds() {
        return timeWindowSeconds;
    }

    public void setTimeWindowSeconds(int timeWindowSeconds) {
        this.timeWindowSeconds = timeWindowSeconds;
    }

    public int getMaxThreadsPerWindow() {
        return maxThreadsPerWindow;
    }

    public void setMaxThreadsPerWindow(int maxThreadsPerWindow) {
        this.maxThreadsPerWindow = maxThreadsPerWindow;
    }

    public int getMaxJobsPerWindow() {
        return maxJobsPerWindow;
    }

    public void setMaxJobsPerWindow(int maxJobsPerWindow) {
        this.maxJobsPerWindow = maxJobsPerWindow;
    }
}

