package com.example.crawler.strategy;

import com.example.crawler.model.TaskResourceEstimate;
import com.example.crawler.model.WorkerResourceStatus;
import com.example.crawler.service.UserResourceLimitService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 默认任务优先级策略实现
 * 
 * <p>实现策略：
 * <ol>
 *   <li>检查用户资源限制：如果用户超过限制，降低优先级或拒绝执行</li>
 *   <li>小资源消耗优先：资源消耗分数小的任务优先执行</li>
 *   <li>等待时间补偿：等待时间长的任务适当提升优先级</li>
 * </ol>
 * </p>
 */
@Component
public class DefaultTaskPriorityStrategy implements TaskPriorityStrategy {

    private static final Logger logger = LoggerFactory.getLogger(DefaultTaskPriorityStrategy.class);

    private final UserResourceLimitService userResourceLimitService;

    // 优先级计算权重
    private static final double RESOURCE_SCORE_WEIGHT = 0.7; // 资源消耗权重
    private static final double WAIT_TIME_WEIGHT = 0.3; // 等待时间权重
    private static final long MAX_WAIT_TIME_MS = 300000; // 最大等待时间5分钟（用于归一化）

    public DefaultTaskPriorityStrategy(UserResourceLimitService userResourceLimitService) {
        this.userResourceLimitService = userResourceLimitService;
    }

    @Override
    public List<PrioritizedTask> prioritize(List<PrioritizedTask> tasks, WorkerResourceStatus resourceStatus) {
        long currentTime = System.currentTimeMillis();

        // 计算每个任务的优先级分数
        for (PrioritizedTask task : tasks) {
            // 1. 检查用户资源限制
            UserResourceLimitService.UserResourceUsage userUsage = 
                    userResourceLimitService.getUserResourceUsage(task.getUserId());
            
            int requiredThreads = task.getResourceEstimate().getEstimatedThreads();
            boolean canUserSubmit = userResourceLimitService.canUserSubmitTask(
                    task.getUserId(), requiredThreads);

            if (!canUserSubmit) {
                // 如果用户超过限制，设置很低的优先级（但不禁用，允许等待）
                task.setPriorityScore(1000.0); // 低优先级
                task.setCanExecute(false);
                continue;
            }

            // 2. 计算资源消耗分数（越小越好）
            double resourceScore = task.getResourceEstimate().getResourceScore();

            // 3. 计算等待时间分数（等待时间越长，优先级越高）
            long waitTime = currentTime - task.getSubmitTime();
            double normalizedWaitTime = Math.min(1.0, (double) waitTime / MAX_WAIT_TIME_MS);

            // 4. 综合计算优先级分数（分数越小，优先级越高）
            // 资源消耗占70%，等待时间占30%
            double priorityScore = resourceScore * RESOURCE_SCORE_WEIGHT - 
                                 normalizedWaitTime * WAIT_TIME_WEIGHT;

            task.setPriorityScore(priorityScore);

            // 5. 检查是否可以立即执行
            task.setCanExecute(canExecuteImmediately(task, resourceStatus));
        }

        // 按优先级分数排序（分数小的在前，即优先级高的在前）
        return tasks.stream()
                .sorted(Comparator.comparingDouble(PrioritizedTask::getPriorityScore))
                .collect(Collectors.toList());
    }

    @Override
    public boolean canExecuteImmediately(PrioritizedTask task, WorkerResourceStatus resourceStatus) {
        // 1. 检查Worker是否有足够资源
        int requiredThreads = task.getResourceEstimate().getEstimatedThreads();
        if (!resourceStatus.hasEnoughResources(requiredThreads)) {
            logger.debug("Not enough resources: required={}, available={}", 
                    requiredThreads, resourceStatus.getAvailableThreads());
            return false;
        }

        // 2. 检查用户资源限制
        int requiredThreadsForTask = task.getResourceEstimate().getEstimatedThreads();
        if (!userResourceLimitService.canUserSubmitTask(task.getUserId(), requiredThreadsForTask)) {
            logger.debug("User resource limit exceeded: userId={}", task.getUserId());
            return false;
        }

        return true;
    }
}

