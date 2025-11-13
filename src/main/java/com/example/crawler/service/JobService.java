package com.example.crawler.service;

import com.example.crawler.config.KafkaConstants;
import com.example.crawler.config.RedisKeyConstants;
import com.example.crawler.dto.JobStatusResponse;
import com.example.crawler.dto.JobSubmitRequest;
import com.example.crawler.dto.JobSubmitResponse;
import com.example.crawler.entity.JobEntity;
import com.example.crawler.exception.JobNotCompletedException;
import com.example.crawler.exception.JobNotFoundException;
import com.example.crawler.model.JobStatus;
import com.example.crawler.repository.JobRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.ListenableFutureCallback;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 爬虫任务服务层
 * 
 * <p>负责处理爬虫任务的核心业务逻辑：
 * <ul>
 *   <li>提交任务到Kafka队列</li>
 *   <li>合并Redis和MySQL的状态数据</li>
 *   <li>获取任务的最终结果</li>
 * </ul>
 * </p>
 */
@Service
public class JobService {

    private static final Logger logger = LoggerFactory.getLogger(JobService.class);

    private final JobRepository jobRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public JobService(JobRepository jobRepository, 
                     RedisTemplate<String, String> redisTemplate,
                     KafkaTemplate<String, String> kafkaTemplate,
                     ObjectMapper objectMapper) {
        this.jobRepository = jobRepository;
        this.redisTemplate = redisTemplate;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 提交爬虫任务
     * 
     * <p>执行流程：
     * <ol>
     *   <li>验证URL列表不为空</li>
     *   <li>生成唯一的jobId（UUID）</li>
     *   <li>创建JobEntity并保存到MySQL（状态为PENDING）</li>
     *   <li>将任务消息发送到Kafka主题</li>
     *   <li>立即返回jobId给客户端</li>
     * </ol>
     * </p>
     * 
     * @param request 包含URL列表的提交请求
     * @return 包含jobId的响应对象
     * @throws IllegalArgumentException 如果URL列表为空或null
     */
    @Transactional
    public JobSubmitResponse submitJob(JobSubmitRequest request) {
        logger.info("Submitting new crawling job with {} URLs", request.urls().size());

        // 验证URL列表
        if (request.urls() == null || request.urls().isEmpty()) {
            throw new IllegalArgumentException("URL list cannot be null or empty");
        }

        // 生成唯一的jobId
        String jobId = UUID.randomUUID().toString();
        logger.debug("Generated jobId: {}", jobId);

        // 创建JobEntity并保存到MySQL
        JobEntity jobEntity = new JobEntity();
        jobEntity.setId(jobId);
        jobEntity.setStatus(JobStatus.PENDING);
        jobEntity.setUrlsSubmitted(request.urls().size());
        jobEntity.setUrlsSucceeded(0);
        jobEntity.setUrlsFailed(0);

        jobRepository.save(jobEntity);
        logger.info("Job entity saved to database: {}", jobId);

        // 构建任务消息（JSON格式）
        Map<String, Object> taskMessage = new HashMap<>();
        taskMessage.put("jobId", jobId);
        taskMessage.put("urls", request.urls());

        try {
            String taskMessageJson = objectMapper.writeValueAsString(taskMessage);
            
            // 将任务发送到Kafka主题（使用jobId作为key，确保同一任务的消息有序）
            ListenableFuture<SendResult<String, String>> future = 
                    kafkaTemplate.send(KafkaConstants.JOB_TOPIC, jobId, taskMessageJson);
            
            // 添加回调处理
            future.addCallback(new ListenableFutureCallback<SendResult<String, String>>() {
                @Override
                public void onSuccess(SendResult<String, String> result) {
                    logger.info("Task message sent to Kafka successfully: jobId={}, offset={}", 
                            jobId, result.getRecordMetadata().offset());
                }

                @Override
                public void onFailure(Throwable ex) {
                    logger.error("Failed to send task message to Kafka: jobId={}", jobId, ex);
                    // 可以考虑更新任务状态为FAILED
                }
            });
            
            logger.info("Task message sent to Kafka topic: {}", jobId);
        } catch (Exception e) {
            logger.error("Failed to serialize or send task message for jobId: {}", jobId, e);
            throw new RuntimeException("Failed to send task message to Kafka", e);
        }

        return new JobSubmitResponse(jobId);
    }

    /**
     * 获取任务状态（合并Redis和MySQL数据）
     * 
     * <p>核心合并逻辑：
     * <ol>
     *   <li>首先尝试从Redis获取实时状态（Worker正在处理时会有）</li>
     *   <li>如果Redis中有实时状态，说明任务正在运行，返回RUNNING状态和实时数据</li>
     *   <li>如果Redis中没有实时状态，从MySQL查询持久化状态</li>
     *   <li>如果MySQL中也没有，抛出JobNotFoundException</li>
     *   <li>如果MySQL中有，返回持久化的状态数据</li>
     * </ol>
     * </p>
     * 
     * @param jobId 任务唯一标识符
     * @return 合并后的任务状态响应
     * @throws JobNotFoundException 如果任务不存在
     */
    public JobStatusResponse getJobStatus(String jobId) {
        logger.debug("Fetching status for jobId: {}", jobId);

        // 尝试从Redis获取实时状态
        String liveStatusKey = RedisKeyConstants.buildLiveStatusKey(jobId);
        String liveStatusJson = redisTemplate.opsForValue().get(liveStatusKey);

        if (liveStatusJson != null && !liveStatusJson.isEmpty()) {
            // Redis中有实时状态，说明Worker正在处理
            logger.debug("Found live status in Redis for jobId: {}", jobId);
            return parseLiveStatus(jobId, liveStatusJson);
        }

        // Redis中没有实时状态，从MySQL查询持久化状态
        Optional<JobEntity> jobEntityOpt = jobRepository.findById(jobId);
        
        if (jobEntityOpt.isEmpty()) {
            logger.warn("Job not found in database: {}", jobId);
            throw new JobNotFoundException("Job not found with id: " + jobId);
        }

        JobEntity jobEntity = jobEntityOpt.get();
        logger.debug("Found job in database: {} with status: {}", jobId, jobEntity.getStatus());

        // 从MySQL返回持久化状态
        return new JobStatusResponse(
                jobEntity.getId(),
                jobEntity.getStatus(),
                null, // 没有实时消息
                jobEntity.getUrlsSubmitted(),
                jobEntity.getUrlsSucceeded(),
                jobEntity.getUrlsFailed()
        );
    }

    /**
     * 解析Redis中的实时状态JSON
     * 
     * @param jobId 任务ID
     * @param liveStatusJson Redis中的实时状态JSON字符串
     * @return 解析后的状态响应
     */
    @SuppressWarnings("unchecked")
    private JobStatusResponse parseLiveStatus(String jobId, String liveStatusJson) {
        try {
            Map<String, Object> liveStatus = objectMapper.readValue(liveStatusJson, Map.class);
            
            String statusStr = (String) liveStatus.getOrDefault("status", "RUNNING");
            String liveMessage = (String) liveStatus.getOrDefault("message", "Processing...");
            Integer urlsSucceeded = getIntegerValue(liveStatus, "urlsSucceeded", 0);
            Integer urlsFailed = getIntegerValue(liveStatus, "urlsFailed", 0);
            Integer urlsSubmitted = getIntegerValue(liveStatus, "urlsSubmitted", 0);

            JobStatus status = JobStatus.valueOf(statusStr);

            return new JobStatusResponse(
                    jobId,
                    status,
                    liveMessage,
                    urlsSubmitted,
                    urlsSucceeded,
                    urlsFailed
            );
        } catch (JsonProcessingException e) {
            logger.error("Failed to parse live status JSON for jobId: {}", jobId, e);
            // 如果解析失败，尝试从MySQL获取
            return getJobStatusFromDatabase(jobId);
        }
    }

    /**
     * 从数据库获取任务状态（辅助方法）
     */
    private JobStatusResponse getJobStatusFromDatabase(String jobId) {
        Optional<JobEntity> jobEntityOpt = jobRepository.findById(jobId);
        if (jobEntityOpt.isEmpty()) {
            throw new JobNotFoundException("Job not found with id: " + jobId);
        }
        JobEntity jobEntity = jobEntityOpt.get();
        return new JobStatusResponse(
                jobEntity.getId(),
                jobEntity.getStatus(),
                null,
                jobEntity.getUrlsSubmitted(),
                jobEntity.getUrlsSucceeded(),
                jobEntity.getUrlsFailed()
        );
    }

    /**
     * 安全地从Map中获取Integer值
     */
    private Integer getIntegerValue(Map<String, Object> map, String key, Integer defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 获取任务的最终结果
     * 
     * <p>从MySQL中读取持久化的HTML结果。
     * 只有当任务状态为SUCCEEDED时才能获取结果。
     * </p>
     * 
     * @param jobId 任务唯一标识符
     * @return 任务的HTML结果内容
     * @throws JobNotFoundException 如果任务不存在
     * @throws JobNotCompletedException 如果任务未成功完成
     */
    public String getJobResult(String jobId) {
        logger.debug("Fetching result for jobId: {}", jobId);

        Optional<JobEntity> jobEntityOpt = jobRepository.findById(jobId);
        
        if (jobEntityOpt.isEmpty()) {
            logger.warn("Job not found in database: {}", jobId);
            throw new JobNotFoundException("Job not found with id: " + jobId);
        }

        JobEntity jobEntity = jobEntityOpt.get();

        // 检查任务状态
        if (jobEntity.getStatus() != JobStatus.SUCCEEDED) {
            logger.warn("Job {} is not completed. Current status: {}", jobId, jobEntity.getStatus());
            throw new JobNotCompletedException(jobId, jobEntity.getStatus().name());
        }

        String resultHtml = jobEntity.getResultHtml();
        if (resultHtml == null || resultHtml.isEmpty()) {
            logger.warn("Job {} result HTML is empty", jobId);
            throw new RuntimeException("Job result HTML is empty");
        }

        logger.info("Successfully retrieved result for jobId: {}", jobId);
        return resultHtml;
    }
}

