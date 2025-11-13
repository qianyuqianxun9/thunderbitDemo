package com.example.crawler.model;

/**
 * 作业状态枚举
 * 
 * <p>定义爬虫任务的所有可能状态：
 * <ul>
 *   <li>PENDING - 任务已提交，等待Worker处理</li>
 *   <li>RUNNING - Worker正在处理任务</li>
 *   <li>SUCCEEDED - 任务成功完成</li>
 *   <li>FAILED - 任务执行失败</li>
 * </ul>
 * </p>
 */
public enum JobStatus {
    /**
     * 待处理状态：任务已提交到队列，等待Worker处理
     */
    PENDING,
    
    /**
     * 运行中状态：Worker正在处理任务
     */
    RUNNING,
    
    /**
     * 成功状态：任务已成功完成
     */
    SUCCEEDED,
    
    /**
     * 失败状态：任务执行失败
     */
    FAILED
}

