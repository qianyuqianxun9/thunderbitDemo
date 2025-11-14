package com.example.crawler.model;

/**
 * 任务资源评估结果
 * 
 * <p>用于评估任务所需的资源消耗，包括线程数和预估执行时长。
 * </p>
 */
public class TaskResourceEstimate {

    /**
     * 预估需要的线程数
     */
    private final int estimatedThreads;

    /**
     * 预估执行时长（毫秒）
     */
    private final long estimatedExecutionTimeMs;

    /**
     * 资源消耗分数（用于优先级排序）
     */
    private final double resourceScore;

    public TaskResourceEstimate(int estimatedThreads, long estimatedExecutionTimeMs, double resourceScore) {
        this.estimatedThreads = estimatedThreads;
        this.estimatedExecutionTimeMs = estimatedExecutionTimeMs;
        this.resourceScore = resourceScore;
    }

    public int getEstimatedThreads() {
        return estimatedThreads;
    }

    public long getEstimatedExecutionTimeMs() {
        return estimatedExecutionTimeMs;
    }

    public double getResourceScore() {
        return resourceScore;
    }
}

