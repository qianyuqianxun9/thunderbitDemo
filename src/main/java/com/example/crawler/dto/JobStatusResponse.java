package com.example.crawler.dto;

import com.example.crawler.model.JobStatus;

/**
 * 查询任务状态的响应DTO
 * 
 * <p>该响应包含了从Redis（实时状态）和MySQL（持久化状态）合并后的任务状态信息。
 * </p>
 * 
 * @param jobId 任务唯一标识符
 * @param status 当前任务状态
 * @param liveMessage Worker上报的实时消息（如果任务正在运行中）
 * @param urlsSubmitted 提交的URL总数
 * @param urlsSucceeded 成功处理的URL数量
 * @param urlsFailed 处理失败的URL数量
 */
public record JobStatusResponse(
        String jobId,
        JobStatus status,
        String liveMessage,
        int urlsSubmitted,
        int urlsSucceeded,
        int urlsFailed
) {
}

