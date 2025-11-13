package com.example.crawler.dto;

/**
 * 提交爬虫任务的响应DTO
 * 
 * @param jobId 生成的任务唯一标识符（UUID格式）
 */
public record JobSubmitResponse(String jobId) {
}

