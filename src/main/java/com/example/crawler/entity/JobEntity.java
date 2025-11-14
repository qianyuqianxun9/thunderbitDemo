package com.example.crawler.entity;

import com.example.crawler.model.JobStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 爬虫任务实体
 * 
 * <p>用于持久化存储爬虫任务的基本信息和最终结果。
 * 该实体存储在MySQL数据库中，用于记录任务的最终状态和结果。
 * </p>
 */
@Entity
@Table(name = "job")
public class JobEntity {

    /**
     * 任务唯一标识符（UUID格式）
     */
    @Id
    @Column(length = 36)
    private String id;

    /**
     * 任务状态
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobStatus status;

    /**
     * 最终爬取结果的HTML内容
     * 使用@Lob注解支持大文本存储
     */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String resultHtml;

    /**
     * 提交的URL总数
     */
    @Column(nullable = false)
    private Integer urlsSubmitted;

    /**
     * 成功处理的URL数量
     */
    @Column(nullable = false)
    private Integer urlsSucceeded = 0;

    /**
     * 处理失败的URL数量
     */
    @Column(nullable = false)
    private Integer urlsFailed = 0;

    /**
     * 用户标识（用于资源限制和优先级分配）
     */
    @Column(length = 100)
    private String userId;

    /**
     * 任务执行时长（毫秒）
     */
    @Column
    private Long executionTimeMs;

    /**
     * 任务开始时间
     */
    @Column
    private LocalDateTime startedAt;

    /**
     * 任务结束时间
     */
    @Column
    private LocalDateTime completedAt;

    /**
     * 创建时间（自动填充）
     */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 更新时间（自动填充）
     */
    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // Constructors
    public JobEntity() {
    }

    public JobEntity(String id, JobStatus status, Integer urlsSubmitted) {
        this.id = id;
        this.status = status;
        this.urlsSubmitted = urlsSubmitted;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public JobStatus getStatus() {
        return status;
    }

    public void setStatus(JobStatus status) {
        this.status = status;
    }

    public String getResultHtml() {
        return resultHtml;
    }

    public void setResultHtml(String resultHtml) {
        this.resultHtml = resultHtml;
    }

    public Integer getUrlsSubmitted() {
        return urlsSubmitted;
    }

    public void setUrlsSubmitted(Integer urlsSubmitted) {
        this.urlsSubmitted = urlsSubmitted;
    }

    public Integer getUrlsSucceeded() {
        return urlsSucceeded;
    }

    public void setUrlsSucceeded(Integer urlsSucceeded) {
        this.urlsSucceeded = urlsSucceeded;
    }

    public Integer getUrlsFailed() {
        return urlsFailed;
    }

    public void setUrlsFailed(Integer urlsFailed) {
        this.urlsFailed = urlsFailed;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Long getExecutionTimeMs() {
        return executionTimeMs;
    }

    public void setExecutionTimeMs(Long executionTimeMs) {
        this.executionTimeMs = executionTimeMs;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}

