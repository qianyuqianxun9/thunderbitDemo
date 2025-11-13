package com.example.crawler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 分布式爬虫服务主应用类
 * 
 * <p>Spring Boot应用的入口点。
 * 
 * <p>@EnableScheduling注解启用定时任务功能，用于MockWorkerService的定时任务。
 * </p>
 */
@SpringBootApplication
@EnableScheduling
public class CrawlerApplication {

    public static void main(String[] args) {
        SpringApplication.run(CrawlerApplication.class, args);
    }
}

