package com.example.crawler.service;

import com.example.crawler.model.TaskResourceEstimate;
import com.example.crawler.repository.JobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 任务资源评估服务
 * 
 * <p>根据历史任务的执行数据，评估新任务所需的资源消耗。
 * 使用历史平均执行时长和URL数量来估算资源需求。
 * </p>
 */
@Service
public class TaskResourceEstimationService {

    private static final Logger logger = LoggerFactory.getLogger(TaskResourceEstimationService.class);

    private final JobRepository jobRepository;

    // 默认配置
    private static final long DEFAULT_EXECUTION_TIME_PER_URL_MS = 2000; // 每个URL默认2秒
    private static final int DEFAULT_THREADS_PER_URL = 1; // 每个URL默认1个线程
    private static final int MIN_THREADS = 1;
    private static final int MAX_THREADS = 10; // 单个任务最多10个线程

    public TaskResourceEstimationService(JobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    /**
     * 评估任务所需的资源
     * 
     * @param urlCount URL数量
     * @param userId 用户ID（可选，用于基于用户历史的评估）
     * @return 资源评估结果
     */
    public TaskResourceEstimate estimateResource(int urlCount, String userId) {
        // 1. 基于历史数据计算平均执行时长
        long avgExecutionTimePerUrl = calculateAverageExecutionTimePerUrl(userId);
        
        // 2. 估算总执行时长
        long estimatedExecutionTime = avgExecutionTimePerUrl * urlCount;
        
        // 3. 估算所需线程数（基于URL数量和执行时长）
        int estimatedThreads = estimateThreads(urlCount, estimatedExecutionTime);
        
        // 4. 计算资源消耗分数（用于优先级排序）
        // 分数 = 线程数 * 执行时长（归一化），分数越小优先级越高
        double resourceScore = calculateResourceScore(estimatedThreads, estimatedExecutionTime);
        
        logger.debug("Resource estimation: urlCount={}, threads={}, time={}ms, score={}", 
                urlCount, estimatedThreads, estimatedExecutionTime, resourceScore);
        
        return new TaskResourceEstimate(estimatedThreads, estimatedExecutionTime, resourceScore);
    }

    /**
     * 计算平均每个URL的执行时长
     */
    private long calculateAverageExecutionTimePerUrl(String userId) {
        try {
            // 查询最近完成的100个任务（如果userId为null，查询所有用户）
            List<Object[]> results = jobRepository.findRecentCompletedJobs(userId, 100);
            
            if (results.isEmpty()) {
                return DEFAULT_EXECUTION_TIME_PER_URL_MS;
            }
            
            long totalTime = 0;
            int totalUrls = 0;
            int validSamples = 0;
            
            for (Object[] result : results) {
                Long executionTime = (Long) result[0];
                Integer urlsSubmitted = (Integer) result[1];
                
                if (executionTime != null && executionTime > 0 && 
                    urlsSubmitted != null && urlsSubmitted > 0) {
                    totalTime += executionTime;
                    totalUrls += urlsSubmitted;
                    validSamples++;
                }
            }
            
            if (validSamples == 0 || totalUrls == 0) {
                return DEFAULT_EXECUTION_TIME_PER_URL_MS;
            }
            
            long avgTimePerUrl = totalTime / totalUrls;
            // 限制在合理范围内（100ms - 30秒）
            return Math.max(100, Math.min(30000, avgTimePerUrl));
            
        } catch (Exception e) {
            logger.warn("Failed to calculate average execution time, using default", e);
            return DEFAULT_EXECUTION_TIME_PER_URL_MS;
        }
    }

    /**
     * 估算所需线程数
     */
    private int estimateThreads(int urlCount, long estimatedExecutionTime) {
        // 简单策略：根据URL数量估算线程数
        // 1-5个URL: 1线程
        // 6-20个URL: 2-3线程
        // 21-50个URL: 4-6线程
        // 50+个URL: 7-10线程
        
        int threads;
        if (urlCount <= 5) {
            threads = 1;
        } else if (urlCount <= 20) {
            threads = Math.min(3, urlCount / 7 + 1);
        } else if (urlCount <= 50) {
            threads = Math.min(6, urlCount / 10 + 2);
        } else {
            threads = Math.min(MAX_THREADS, urlCount / 10 + 3);
        }
        
        return Math.max(MIN_THREADS, Math.min(MAX_THREADS, threads));
    }

    /**
     * 计算资源消耗分数
     */
    private double calculateResourceScore(int threads, long executionTimeMs) {
        // 归一化：线程数权重0.6，执行时长权重0.4
        // 执行时长归一化到0-1（假设最大30秒）
        double normalizedTime = Math.min(1.0, executionTimeMs / 30000.0);
        double normalizedThreads = Math.min(1.0, threads / 10.0);
        
        return normalizedThreads * 0.6 + normalizedTime * 0.4;
    }
}

