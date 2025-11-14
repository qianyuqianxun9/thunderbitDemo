package com.example.crawler.service;

import com.example.crawler.config.WorkerResourceConfig;
import com.example.crawler.model.WorkerResourceStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Worker资源监控服务
 * 
 * <p>实时监控Worker集群的资源使用情况，包括：
 * <ul>
 *   <li>当前正在执行的任务数量</li>
 *   <li>当前使用的线程数</li>
 *   <li>可用资源量</li>
 * </ul>
 * </p>
 */
@Service
public class WorkerResourceMonitorService {

    private static final Logger logger = LoggerFactory.getLogger(WorkerResourceMonitorService.class);

    private static final String REDIS_KEY_RUNNING_JOBS = "crawler:worker:running:jobs";
    private static final String REDIS_KEY_THREAD_USAGE = "crawler:worker:thread:usage";

    private final WorkerResourceConfig workerResourceConfig;
    private final RedisTemplate<String, String> redisTemplate;

    // 本地缓存当前资源使用情况（定期从Redis同步）
    private final AtomicInteger currentUsedThreads = new AtomicInteger(0);
    private final AtomicInteger currentUsedInstances = new AtomicInteger(0);

    public WorkerResourceMonitorService(WorkerResourceConfig workerResourceConfig,
                                       RedisTemplate<String, String> redisTemplate) {
        this.workerResourceConfig = workerResourceConfig;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 获取当前Worker资源状态
     */
    public WorkerResourceStatus getCurrentResourceStatus() {
        // 从Redis读取当前资源使用情况
        updateResourceUsageFromRedis();
        
        return new WorkerResourceStatus(
                workerResourceConfig.getTotalInstances(),
                workerResourceConfig.getTotalThreads(),
                currentUsedInstances.get(),
                currentUsedThreads.get()
        );
    }

    /**
     * 从Redis更新资源使用情况
     */
    private void updateResourceUsageFromRedis() {
        try {
            // 获取所有正在运行的任务
            Set<String> runningJobs = redisTemplate.opsForSet().members(REDIS_KEY_RUNNING_JOBS);
            int runningJobCount = runningJobs != null ? runningJobs.size() : 0;
            
            // 从Redis读取线程使用数（如果存在）
            String threadUsageStr = redisTemplate.opsForValue().get(REDIS_KEY_THREAD_USAGE);
            int threadUsage = 0;
            if (threadUsageStr != null) {
                try {
                    threadUsage = Integer.parseInt(threadUsageStr);
                } catch (NumberFormatException e) {
                    logger.warn("Invalid thread usage value in Redis: {}", threadUsageStr);
                }
            }
            
            // 如果没有Redis数据，使用运行任务数估算
            if (threadUsage == 0 && runningJobCount > 0) {
                // 简单估算：每个任务平均使用2个线程
                threadUsage = runningJobCount * 2;
            }
            
            currentUsedThreads.set(Math.min(threadUsage, workerResourceConfig.getTotalThreads()));
            currentUsedInstances.set(Math.min(runningJobCount, workerResourceConfig.getTotalInstances()));
            
        } catch (Exception e) {
            logger.error("Failed to update resource usage from Redis", e);
        }
    }

    /**
     * 注册任务开始执行（增加资源使用）
     */
    public void registerJobStart(String jobId, int threadCount) {
        try {
            redisTemplate.opsForSet().add(REDIS_KEY_RUNNING_JOBS, jobId);
            redisTemplate.expire(REDIS_KEY_RUNNING_JOBS, Duration.ofHours(1));
            
            // 更新线程使用数
            redisTemplate.opsForValue().increment(REDIS_KEY_THREAD_USAGE, threadCount);
            redisTemplate.expire(REDIS_KEY_THREAD_USAGE, Duration.ofHours(1));
            
            currentUsedThreads.addAndGet(threadCount);
            currentUsedInstances.incrementAndGet();
            
            logger.debug("Registered job start: jobId={}, threads={}", jobId, threadCount);
        } catch (Exception e) {
            logger.error("Failed to register job start: jobId={}", jobId, e);
        }
    }

    /**
     * 注册任务完成（释放资源）
     */
    public void registerJobComplete(String jobId, int threadCount) {
        try {
            redisTemplate.opsForSet().remove(REDIS_KEY_RUNNING_JOBS, jobId);
            
            // 更新线程使用数（减少）
            Long newValue = redisTemplate.opsForValue().decrement(REDIS_KEY_THREAD_USAGE, threadCount);
            if (newValue != null && newValue < 0) {
                // 如果出现负数，重置为0
                redisTemplate.opsForValue().set(REDIS_KEY_THREAD_USAGE, "0");
            }
            
            currentUsedThreads.addAndGet(-threadCount);
            currentUsedInstances.decrementAndGet();
            
            logger.debug("Registered job complete: jobId={}, threads={}", jobId, threadCount);
        } catch (Exception e) {
            logger.error("Failed to register job complete: jobId={}", jobId, e);
        }
    }

    /**
     * 定期清理过期的资源记录（每5分钟执行一次）
     */
    @Scheduled(fixedRate = 300000)
    public void cleanupExpiredResources() {
        try {
            updateResourceUsageFromRedis();
            logger.debug("Resource status: threads={}/{}, instances={}/{}", 
                    currentUsedThreads.get(), workerResourceConfig.getTotalThreads(),
                    currentUsedInstances.get(), workerResourceConfig.getTotalInstances());
        } catch (Exception e) {
            logger.error("Failed to cleanup expired resources", e);
        }
    }
}

