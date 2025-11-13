package com.example.crawler.exception;

/**
 * 任务未完成异常
 * 
 * <p>当尝试获取任务结果，但任务状态不是SUCCEEDED时抛出此异常。
 * </p>
 */
public class JobNotCompletedException extends RuntimeException {

    public JobNotCompletedException(String message) {
        super(message);
    }

    public JobNotCompletedException(String jobId, String currentStatus) {
        super(String.format("Job [%s] is not completed. Current status: %s", jobId, currentStatus));
    }
}

