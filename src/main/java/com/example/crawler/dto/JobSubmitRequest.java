package com.example.crawler.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 提交爬虫任务的请求DTO
 * 
 * @param urls 待爬取的URL列表，不能为空
 */
public record JobSubmitRequest(
        @NotNull(message = "URL列表不能为null")
        @NotEmpty(message = "URL列表不能为空")
        List<String> urls
) {
}

