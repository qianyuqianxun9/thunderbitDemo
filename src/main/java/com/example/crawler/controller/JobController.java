package com.example.crawler.controller;

import com.example.crawler.dto.JobStatusResponse;
import com.example.crawler.dto.JobSubmitRequest;
import com.example.crawler.dto.JobSubmitResponse;
import com.example.crawler.service.JobService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 爬虫任务API控制器
 * 
 * <p>提供三个核心RESTful API：
 * <ul>
 *   <li>POST /api/v1/jobs - 提交爬虫任务</li>
 *   <li>GET /api/v1/jobs/{jobId}/status - 查询任务状态</li>
 *   <li>GET /api/v1/jobs/{jobId}/result - 获取任务结果</li>
 * </ul>
 * </p>
 */
@RestController
@RequestMapping("/api/v1/jobs")
public class JobController {

    private static final Logger logger = LoggerFactory.getLogger(JobController.class);

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    /**
     * 提交爬虫任务
     * 
     * <p>接收URL列表，创建任务并推送到Redis队列，立即返回jobId。
     * </p>
     * 
     * @param request 包含URL列表的请求
     * @return 包含jobId的响应
     */
    @PostMapping
    public ResponseEntity<JobSubmitResponse> submitJob(@Valid @RequestBody JobSubmitRequest request) {
        logger.info("Received job submission request with {} URLs", request.urls().size());
        
        JobSubmitResponse response = jobService.submitJob(request);
        
        logger.info("Job submitted successfully: jobId={}", response.jobId());
        return ResponseEntity.ok(response);
    }

    /**
     * 查询任务状态
     * 
     * <p>合并Redis（实时状态）和MySQL（持久化状态）的数据，返回当前任务状态。
     * </p>
     * 
     * @param jobId 任务唯一标识符
     * @return 任务状态响应
     */
    @GetMapping("/{jobId}/status")
    public ResponseEntity<JobStatusResponse> getJobStatus(@PathVariable String jobId) {
        logger.debug("Received status query request for jobId: {}", jobId);
        
        JobStatusResponse response = jobService.getJobStatus(jobId);
        
        return ResponseEntity.ok(response);
    }

    /**
     * 获取任务结果
     * 
     * <p>从MySQL中读取任务的最终HTML结果。
     * 只有当任务状态为SUCCEEDED时才能获取结果。
     * </p>
     * 
     * @param jobId 任务唯一标识符
     * @return HTML格式的任务结果
     */
    @GetMapping("/{jobId}/result")
    public ResponseEntity<String> getJobResult(@PathVariable String jobId) {
        logger.debug("Received result query request for jobId: {}", jobId);
        
        String htmlContent = jobService.getJobResult(jobId);
        
        logger.info("Returning result for jobId: {}", jobId);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(htmlContent);
    }
}

