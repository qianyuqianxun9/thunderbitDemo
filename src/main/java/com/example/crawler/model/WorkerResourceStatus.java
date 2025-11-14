package com.example.crawler.model;

/**
 * Worker资源状态
 * 
 * <p>表示当前Worker集群的资源使用情况。
 * </p>
 */
public class WorkerResourceStatus {

    /**
     * 总实例数
     */
    private final int totalInstances;

    /**
     * 总线程数
     */
    private final int totalThreads;

    /**
     * 当前使用的线程数
     */
    private final int usedThreads;

    /**
     * 当前使用的实例数
     */
    private final int usedInstances;

    /**
     * 可用线程数
     */
    private final int availableThreads;

    /**
     * 可用实例数
     */
    private final int availableInstances;

    /**
     * 资源使用率（0.0 - 1.0）
     */
    private final double utilizationRate;

    public WorkerResourceStatus(int totalInstances, int totalThreads, 
                               int usedInstances, int usedThreads) {
        this.totalInstances = totalInstances;
        this.totalThreads = totalThreads;
        this.usedInstances = usedInstances;
        this.usedThreads = usedThreads;
        this.availableInstances = totalInstances - usedInstances;
        this.availableThreads = totalThreads - usedThreads;
        this.utilizationRate = totalThreads > 0 ? (double) usedThreads / totalThreads : 0.0;
    }

    public int getTotalInstances() {
        return totalInstances;
    }

    public int getTotalThreads() {
        return totalThreads;
    }

    public int getUsedThreads() {
        return usedThreads;
    }

    public int getUsedInstances() {
        return usedInstances;
    }

    public int getAvailableThreads() {
        return availableThreads;
    }

    public int getAvailableInstances() {
        return availableInstances;
    }

    public double getUtilizationRate() {
        return utilizationRate;
    }

    /**
     * 检查是否有足够的资源执行任务
     */
    public boolean hasEnoughResources(int requiredThreads) {
        return availableThreads >= requiredThreads && availableInstances > 0;
    }
}

