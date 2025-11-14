package com.example.crawler.service;

import com.example.crawler.config.UserResourceLimitConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 用户资源限制服务
 * 
 * <p>监控和管理每个用户在指定时间窗口内的资源消耗，
 * 防止单个用户占用过多资源导致其他用户饥饿。
 * </p>
 */
@Service
public class UserResourceLimitService {

    private static final Logger logger = LoggerFactory.getLogger(UserResourceLimitService.class);

    private static final String REDIS_KEY_USER_THREADS = "crawler:user:threads:";
    private static final String REDIS_KEY_USER_JOBS = "crawler:user:jobs:";

    private final UserResourceLimitConfig limitConfig;
    private final RedisTemplate<String, String> redisTemplate;

    public UserResourceLimitService(UserResourceLimitConfig limitConfig,
                                   RedisTemplate<String, String> redisTemplate) {
        this.limitConfig = limitConfig;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 检查用户是否可以提交新任务
     * 
     * @param userId 用户ID
     * @param requiredThreads 所需线程数
     * @return true如果可以提交，false如果超过限制
     */
    public boolean canUserSubmitTask(String userId, int requiredThreads) {
        if (userId == null || userId.isEmpty()) {
            // 如果没有用户ID，允许提交（但建议在生产环境中要求用户认证）
            return true;
        }

        try {
            // 检查线程数限制
            String threadKey = REDIS_KEY_USER_THREADS + userId;
            String threadUsageStr = redisTemplate.opsForValue().get(threadKey);
            int currentThreadUsage = threadUsageStr != null ? Integer.parseInt(threadUsageStr) : 0;
            
            if (currentThreadUsage + requiredThreads > limitConfig.getMaxThreadsPerWindow()) {
                logger.warn("User {} exceeded thread limit: current={}, required={}, max={}", 
                        userId, currentThreadUsage, requiredThreads, limitConfig.getMaxThreadsPerWindow());
                return false;
            }

            // 检查任务数限制
            String jobKey = REDIS_KEY_USER_JOBS + userId;
            String jobCountStr = redisTemplate.opsForValue().get(jobKey);
            int currentJobCount = jobCountStr != null ? Integer.parseInt(jobCountStr) : 0;
            
            if (currentJobCount >= limitConfig.getMaxJobsPerWindow()) {
                logger.warn("User {} exceeded job count limit: current={}, max={}", 
                        userId, currentJobCount, limitConfig.getMaxJobsPerWindow());
                return false;
            }

            return true;
        } catch (Exception e) {
            logger.error("Failed to check user resource limit: userId={}", userId, e);
            // 出错时允许提交，避免阻塞
            return true;
        }
    }

    /**
     * 记录用户开始使用资源
     */
    public void recordUserResourceUsage(String userId, int threadCount) {
        if (userId == null || userId.isEmpty()) {
            return;
        }

        try {
            String threadKey = REDIS_KEY_USER_THREADS + userId;
            String jobKey = REDIS_KEY_USER_JOBS + userId;

            // 增加线程使用数
            redisTemplate.opsForValue().increment(threadKey, threadCount);
            redisTemplate.expire(threadKey, Duration.ofSeconds(limitConfig.getTimeWindowSeconds()));

            // 增加任务数
            redisTemplate.opsForValue().increment(jobKey, 1);
            redisTemplate.expire(jobKey, Duration.ofSeconds(limitConfig.getTimeWindowSeconds()));

            logger.debug("Recorded user resource usage: userId={}, threads={}", userId, threadCount);
        } catch (Exception e) {
            logger.error("Failed to record user resource usage: userId={}", userId, e);
        }
    }

    /**
     * 释放用户资源（任务完成时调用）
     */
    public void releaseUserResource(String userId, int threadCount) {
        if (userId == null || userId.isEmpty()) {
            return;
        }

        try {
            String threadKey = REDIS_KEY_USER_THREADS + userId;
            
            // 减少线程使用数
            Long newValue = redisTemplate.opsForValue().decrement(threadKey, threadCount);
            if (newValue != null && newValue < 0) {
                redisTemplate.opsForValue().set(threadKey, "0");
            }

            logger.debug("Released user resource: userId={}, threads={}", userId, threadCount);
        } catch (Exception e) {
            logger.error("Failed to release user resource: userId={}", userId, e);
        }
    }

    /**
     * 获取用户当前资源使用情况
     */
    public UserResourceUsage getUserResourceUsage(String userId) {
        if (userId == null || userId.isEmpty()) {
            return new UserResourceUsage(0, 0);
        }

        try {
            String threadKey = REDIS_KEY_USER_THREADS + userId;
            String jobKey = REDIS_KEY_USER_JOBS + userId;

            String threadUsageStr = redisTemplate.opsForValue().get(threadKey);
            String jobCountStr = redisTemplate.opsForValue().get(jobKey);

            int threadUsage = threadUsageStr != null ? Integer.parseInt(threadUsageStr) : 0;
            int jobCount = jobCountStr != null ? Integer.parseInt(jobCountStr) : 0;

            return new UserResourceUsage(threadUsage, jobCount);
        } catch (Exception e) {
            logger.error("Failed to get user resource usage: userId={}", userId, e);
            return new UserResourceUsage(0, 0);
        }
    }

    /**
     * 用户资源使用情况
     */
    public static class UserResourceUsage {
        private final int threadUsage;
        private final int jobCount;

        public UserResourceUsage(int threadUsage, int jobCount) {
            this.threadUsage = threadUsage;
            this.jobCount = jobCount;
        }

        public int getThreadUsage() {
            return threadUsage;
        }

        public int getJobCount() {
            return jobCount;
        }
    }
}

