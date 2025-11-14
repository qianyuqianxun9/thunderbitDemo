package com.example.crawler.service;

import com.example.crawler.model.TaskResourceEstimate;
import com.example.crawler.model.WorkerResourceStatus;
import com.example.crawler.strategy.DefaultTaskPriorityStrategy;
import com.example.crawler.strategy.TaskPriorityStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 任务优先级服务
 * 
 * <p>管理待处理任务的优先级队列，使用策略模式进行任务排序和分配。
 * </p>
 */
@Service
public class TaskPriorityService {

    private static final Logger logger = LoggerFactory.getLogger(TaskPriorityService.class);

    private final TaskPriorityStrategy priorityStrategy;
    private final TaskResourceEstimationService resourceEstimationService;
    private final WorkerResourceMonitorService resourceMonitorService;

    // 待处理任务队列（内存队列，实际生产环境可以考虑使用Redis Sorted Set）
    private final Map<String, TaskPriorityStrategy.PrioritizedTask> pendingTasks = new ConcurrentHashMap<>();

    public TaskPriorityService(DefaultTaskPriorityStrategy priorityStrategy,
                             TaskResourceEstimationService resourceEstimationService,
                             WorkerResourceMonitorService resourceMonitorService) {
        this.priorityStrategy = priorityStrategy;
        this.resourceEstimationService = resourceEstimationService;
        this.resourceMonitorService = resourceMonitorService;
    }

    // 保存任务的URL列表（key: jobId, value: urls）
    private final Map<String, List<String>> taskUrls = new ConcurrentHashMap<>();

    /**
     * 添加任务到优先级队列
     */
    public void addTask(String jobId, String userId, List<String> urls) {
        try {
            // 评估任务资源需求
            TaskResourceEstimate estimate = resourceEstimationService.estimateResource(
                    urls.size(), userId);

            // 创建优先级任务
            TaskPriorityStrategy.PrioritizedTask task = 
                    new TaskPriorityStrategy.PrioritizedTask(
                            jobId, userId, urls.size(), estimate, System.currentTimeMillis());

            pendingTasks.put(jobId, task);
            taskUrls.put(jobId, urls); // 保存URL列表
            
            logger.debug("Added task to priority queue: jobId={}, userId={}, urls={}", 
                    jobId, userId, urls.size());
        } catch (Exception e) {
            logger.error("Failed to add task to priority queue: jobId={}", jobId, e);
        }
    }

    /**
     * 获取任务的URL列表
     */
    public List<String> getTaskUrls(String jobId) {
        return taskUrls.get(jobId);
    }

    /**
     * 获取下一个可执行的任务（按优先级）
     */
    public TaskPriorityStrategy.PrioritizedTask getNextExecutableTask() {
        if (pendingTasks.isEmpty()) {
            return null;
        }

        try {
            // 获取当前资源状态
            WorkerResourceStatus resourceStatus = resourceMonitorService.getCurrentResourceStatus();

            // 获取所有待处理任务
            List<TaskPriorityStrategy.PrioritizedTask> tasks = new ArrayList<>(pendingTasks.values());

            // 使用策略进行优先级排序
            List<TaskPriorityStrategy.PrioritizedTask> prioritizedTasks = 
                    priorityStrategy.prioritize(tasks, resourceStatus);

            // 找到第一个可执行的任务
            for (TaskPriorityStrategy.PrioritizedTask task : prioritizedTasks) {
                if (priorityStrategy.canExecuteImmediately(task, resourceStatus)) {
                    // 从队列中移除
                    pendingTasks.remove(task.getJobId());
                    logger.debug("Selected task for execution: jobId={}, priority={}", 
                            task.getJobId(), task.getPriorityScore());
                    return task;
                }
            }

            logger.debug("No executable task found. Pending tasks: {}", pendingTasks.size());
            return null;
        } catch (Exception e) {
            logger.error("Failed to get next executable task", e);
            return null;
        }
    }

    /**
     * 移除任务（任务完成或失败时调用）
     */
    public void removeTask(String jobId) {
        pendingTasks.remove(jobId);
        taskUrls.remove(jobId);
        logger.debug("Removed task from priority queue: jobId={}", jobId);
    }

    /**
     * 获取待处理任务数量
     */
    public int getPendingTaskCount() {
        return pendingTasks.size();
    }
}

