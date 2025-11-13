package com.example.crawler.exception;

/**
 * 任务未找到异常
 * 
 * <p>当查询的任务ID在数据库中不存在时抛出此异常。
 * </p>
 */
public class JobNotFoundException extends RuntimeException {

    public JobNotFoundException(String message) {
        super(message);
    }

    public JobNotFoundException(String jobId, Throwable cause) {
        super("Job not found with id: " + jobId, cause);
    }
}

