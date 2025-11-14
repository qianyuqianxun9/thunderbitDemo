package com.example.crawler.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Worker资源配置
 * 
 * <p>从配置文件读取Worker集群的资源容量配置。
 * </p>
 */
@Configuration
@ConfigurationProperties(prefix = "crawler.worker")
public class WorkerResourceConfig {

    /**
     * Worker实例总数
     */
    private int totalInstances = 1;

    /**
     * 每个实例的最大线程数
     */
    private int maxThreadsPerInstance = 10;

    /**
     * 总线程数（自动计算）
     */
    public int getTotalThreads() {
        return totalInstances * maxThreadsPerInstance;
    }

    public int getTotalInstances() {
        return totalInstances;
    }

    public void setTotalInstances(int totalInstances) {
        this.totalInstances = totalInstances;
    }

    public int getMaxThreadsPerInstance() {
        return maxThreadsPerInstance;
    }

    public void setMaxThreadsPerInstance(int maxThreadsPerInstance) {
        this.maxThreadsPerInstance = maxThreadsPerInstance;
    }
}

