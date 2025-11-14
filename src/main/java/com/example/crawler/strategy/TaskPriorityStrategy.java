package com.example.crawler.strategy;

import com.example.crawler.model.TaskResourceEstimate;
import com.example.crawler.model.WorkerResourceStatus;

import java.util.List;
import java.util.Map;

/**
 * 任务优先级策略接口
 * 
 * <p>定义任务优先级分配的策略模式接口。
 * 不同的实现可以提供不同的优先级算法。
 * </p>
 */
public interface TaskPriorityStrategy {

    /**
     * 对任务列表进行优先级排序
     * 
     * @param tasks 待排序的任务列表，每个任务包含jobId、userId、资源评估等信息
     * @param resourceStatus 当前Worker资源状态
     * @return 排序后的任务列表（优先级高的在前）
     */
    List<PrioritizedTask> prioritize(List<PrioritizedTask> tasks, WorkerResourceStatus resourceStatus);

    /**
     * 检查任务是否可以立即执行
     * 
     * @param task 待检查的任务
     * @param resourceStatus 当前Worker资源状态
     * @return true如果可以立即执行，false需要等待
     */
    boolean canExecuteImmediately(PrioritizedTask task, WorkerResourceStatus resourceStatus);

    /**
     * 带优先级的任务包装类
     */
    class PrioritizedTask {
        private final String jobId;
        private final String userId;
        private final int urlCount;
        private final TaskResourceEstimate resourceEstimate;
        private final long submitTime;
        private double priorityScore;
        private boolean canExecute;

        public PrioritizedTask(String jobId, String userId, int urlCount, 
                              TaskResourceEstimate resourceEstimate, long submitTime) {
            this.jobId = jobId;
            this.userId = userId;
            this.urlCount = urlCount;
            this.resourceEstimate = resourceEstimate;
            this.submitTime = submitTime;
            this.priorityScore = 0.0;
            this.canExecute = false;
        }

        // Getters and Setters
        public String getJobId() {
            return jobId;
        }

        public String getUserId() {
            return userId;
        }

        public int getUrlCount() {
            return urlCount;
        }

        public TaskResourceEstimate getResourceEstimate() {
            return resourceEstimate;
        }

        public long getSubmitTime() {
            return submitTime;
        }

        public double getPriorityScore() {
            return priorityScore;
        }

        public void setPriorityScore(double priorityScore) {
            this.priorityScore = priorityScore;
        }

        public boolean isCanExecute() {
            return canExecute;
        }

        public void setCanExecute(boolean canExecute) {
            this.canExecute = canExecute;
        }
    }
}

