package com.example.crawler.service;

import com.example.crawler.config.KafkaConstants;
import com.example.crawler.config.RedisKeyConstants;
import com.example.crawler.entity.JobEntity;
import com.example.crawler.model.JobStatus;
import com.example.crawler.repository.JobRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.crawler.service.TaskPriorityService;
import com.example.crawler.service.WorkerResourceMonitorService;
import com.example.crawler.service.UserResourceLimitService;
import com.example.crawler.strategy.TaskPriorityStrategy;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 爬虫任务消费者服务
 * 
 * <p>从Kafka主题消费爬虫任务，执行真实的网页爬取逻辑：
 * <ul>
 *   <li>从Kafka消费任务消息</li>
 *   <li>使用HttpClient发送HTTP请求获取网页内容</li>
 *   <li>使用Jsoup解析HTML内容</li>
 *   <li>更新Redis实时状态</li>
 *   <li>完成后更新MySQL并清理Redis状态</li>
 * </ul>
 * </p>
 */
@Service
public class CrawlerConsumerService {

    private static final Logger logger = LoggerFactory.getLogger(CrawlerConsumerService.class);

    private final JobRepository jobRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final CloseableHttpClient httpClient;
    private final TaskPriorityService taskPriorityService;
    private final WorkerResourceMonitorService resourceMonitorService;
    private final UserResourceLimitService userResourceLimitService;

    // HTTP请求配置
    private static final int CONNECT_TIMEOUT = 10000; // 10秒
    private static final int READ_TIMEOUT = 30000; // 30秒
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    public CrawlerConsumerService(JobRepository jobRepository,
                                  RedisTemplate<String, String> redisTemplate,
                                  ObjectMapper objectMapper,
                                  TaskPriorityService taskPriorityService,
                                  WorkerResourceMonitorService resourceMonitorService,
                                  UserResourceLimitService userResourceLimitService) {
        this.jobRepository = jobRepository;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.taskPriorityService = taskPriorityService;
        this.resourceMonitorService = resourceMonitorService;
        this.userResourceLimitService = userResourceLimitService;
        
        // 创建HTTP客户端
        this.httpClient = HttpClients.createDefault();
    }

    /**
     * Kafka消费者：消费爬虫任务消息
     * 
     * <p>使用@KafkaListener注解监听Kafka主题。
     * 收到消息后，将任务添加到优先级队列，而不是立即处理。
     * </p>
     * 
     * @param message 任务消息JSON字符串
     * @param acknowledgment 手动提交offset
     * @param partition Kafka分区
     * @param offset 消息偏移量
     */
    @KafkaListener(topics = KafkaConstants.JOB_TOPIC, 
                   groupId = "${spring.kafka.consumer.group-id}")
    public void consumeCrawlerJob(@Payload String message,
                                  Acknowledgment acknowledgment,
                                  @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                  @Header(KafkaHeaders.OFFSET) long offset) {
        logger.info("Received crawler job message from partition {} offset {}: {}", partition, offset, message);

        try {
            // 解析任务消息
            Map<String, Object> taskMessage = parseTaskMessage(message);
            String jobId = (String) taskMessage.get("jobId");
            @SuppressWarnings("unchecked")
            List<String> urls = (List<String>) taskMessage.get("urls");
            String userId = (String) taskMessage.get("userId");

            if (jobId == null || urls == null || urls.isEmpty()) {
                logger.error("Invalid task message format: {}", message);
                acknowledgment.acknowledge();
                return;
            }

            // 将任务添加到优先级队列（不立即处理）
            taskPriorityService.addTask(jobId, userId, urls);
            logger.info("Task added to priority queue: jobId={}, userId={}, urls={}", 
                    jobId, userId, urls.size());

            // 立即提交offset（任务已在优先级队列中）
            acknowledgment.acknowledge();

        } catch (Exception e) {
            logger.error("Error processing crawler job message", e);
            // 发生错误时不提交offset，让消息重新消费
        }
    }

    /**
     * 定时任务：从优先级队列中获取任务并处理（每2秒执行一次）
     */
    @Scheduled(fixedDelay = 2000)
    public void processPrioritizedTasks() {
        try {
            // 从优先级队列获取下一个可执行的任务
            TaskPriorityStrategy.PrioritizedTask task = taskPriorityService.getNextExecutableTask();
            
            if (task == null) {
                // 没有可执行的任务
                return;
            }

            logger.info("Processing prioritized task: jobId={}, userId={}, priority={}", 
                    task.getJobId(), task.getUserId(), task.getPriorityScore());

            // 获取任务的URL列表
            List<String> urls = taskPriorityService.getTaskUrls(task.getJobId());
            if (urls == null || urls.isEmpty()) {
                logger.error("URLs not found for task: {}", task.getJobId());
                taskPriorityService.removeTask(task.getJobId());
                return;
            }

            // 注册资源使用
            int threadCount = task.getResourceEstimate().getEstimatedThreads();
            resourceMonitorService.registerJobStart(task.getJobId(), threadCount);
            userResourceLimitService.recordUserResourceUsage(task.getUserId(), threadCount);

            // 处理任务
            processCrawlerTask(task.getJobId(), urls, task.getUserId(), threadCount);
            
        } catch (Exception e) {
            logger.error("Error processing prioritized tasks", e);
        }
    }

    /**
     * 解析任务消息JSON
     */
    private Map<String, Object> parseTaskMessage(String taskMessageJson) {
        try {
            return objectMapper.readValue(taskMessageJson, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            logger.error("Failed to parse task message: {}", taskMessageJson, e);
            throw new RuntimeException("Failed to parse task message", e);
        }
    }

    /**
     * 处理爬虫任务
     * 
     * <p>执行流程：
     * <ol>
     *   <li>更新Redis实时状态为RUNNING</li>
     *   <li>遍历URL列表，逐个爬取</li>
     *   <li>使用HttpClient发送HTTP请求</li>
     *   <li>使用Jsoup解析HTML</li>
     *   <li>收集所有URL的HTML内容</li>
     *   <li>更新MySQL：设置状态为SUCCEEDED，填充结果HTML</li>
     *   <li>清理Redis实时状态</li>
     *   <li>释放资源</li>
     * </ol>
     * </p>
     */
    @Transactional
    public void processCrawlerTask(String jobId, List<String> urls, String userId, int threadCount) {
        logger.info("Processing crawler task: jobId={}, userId={}, urlsCount={}, threads={}", 
                jobId, userId, urls.size(), threadCount);

        long startTime = System.currentTimeMillis();
        int totalUrls = urls.size();
        int succeeded = 0;
        int failed = 0;
        StringBuilder resultHtml = new StringBuilder();

        try {
            // 1. 更新任务开始时间
            Optional<JobEntity> jobEntityOpt = jobRepository.findById(jobId);
            if (jobEntityOpt.isPresent()) {
                JobEntity jobEntity = jobEntityOpt.get();
                jobEntity.setStartedAt(java.time.LocalDateTime.now());
                jobRepository.save(jobEntity);
            }

            // 2. 更新Redis实时状态为RUNNING
            updateLiveStatus(jobId, JobStatus.RUNNING, totalUrls, 0, 0, "Starting to crawl...");

            // 2. 构建结果HTML头部
            resultHtml.append("<!DOCTYPE html>\n");
            resultHtml.append("<html><head><title>Crawling Results</title>");
            resultHtml.append("<meta charset=\"UTF-8\">");
            resultHtml.append("<style>body{font-family:Arial,sans-serif;margin:20px;}");
            resultHtml.append(".url-section{margin:20px 0;padding:15px;border:1px solid #ddd;border-radius:5px;}");
            resultHtml.append(".url-header{color:#333;font-size:18px;margin-bottom:10px;}");
            resultHtml.append(".error{color:red;}</style></head><body>\n");
            resultHtml.append("<h1>Crawling Results</h1>\n");
            resultHtml.append("<p>Total URLs: ").append(totalUrls).append("</p>\n");
            resultHtml.append("<p>Started at: ").append(java.time.LocalDateTime.now()).append("</p>\n");
            resultHtml.append("<hr>\n");

            // 3. 遍历URL列表，逐个爬取
            for (int i = 0; i < urls.size(); i++) {
                String url = urls.get(i);
                logger.info("Crawling URL {}/{}: {}", i + 1, totalUrls, url);

                try {
                    // 更新进度
                    updateLiveStatus(jobId, JobStatus.RUNNING, totalUrls, succeeded, failed,
                            String.format("Crawling %d/%d URLs... (Current: %s)", i + 1, totalUrls, url));

                    // 爬取单个URL
                    String urlHtml = crawlUrl(url);
                    succeeded++;

                    // 添加到结果HTML
                    resultHtml.append("<div class=\"url-section\">\n");
                    resultHtml.append("<div class=\"url-header\">✓ Success: <a href=\"").append(url).append("\" target=\"_blank\">")
                            .append(url).append("</a></div>\n");
                    resultHtml.append("<div style=\"max-height:300px;overflow:auto;border:1px solid #eee;padding:10px;\">\n");
                    resultHtml.append(urlHtml);
                    resultHtml.append("</div>\n");
                    resultHtml.append("</div>\n");
    
                    logger.info("Successfully crawled URL: {}", url);

                } catch (Exception e) {
                    failed++;
                    logger.error("Failed to crawl URL: {}", url, e);

                    // 添加错误信息到结果HTML
                    resultHtml.append("<div class=\"url-section\">\n");
                    resultHtml.append("<div class=\"url-header error\">✗ Failed: <a href=\"").append(url)
                            .append("\" target=\"_blank\">").append(url).append("</a></div>\n");
                    resultHtml.append("<div class=\"error\">Error: ").append(escapeHtml(e.getMessage())).append("</div>\n");
                    resultHtml.append("</div>\n");
                }
            }

            // 4. 完成结果HTML
            resultHtml.append("<hr>\n");
            resultHtml.append("<p><strong>Summary:</strong></p>\n");
            resultHtml.append("<ul>\n");
            resultHtml.append("<li>Total: ").append(totalUrls).append("</li>\n");
            resultHtml.append("<li>Succeeded: ").append(succeeded).append("</li>\n");
            resultHtml.append("<li>Failed: ").append(failed).append("</li>\n");
            resultHtml.append("</ul>\n");
            resultHtml.append("<p>Completed at: ").append(java.time.LocalDateTime.now()).append("</p>\n");
            resultHtml.append("</body></html>");

            // 5. 计算执行时长
            long executionTime = System.currentTimeMillis() - startTime;

            // 6. 从MySQL加载JobEntity
            Optional<JobEntity> jobEntityOpt = jobRepository.findById(jobId);
            if (jobEntityOpt.isEmpty()) {
                logger.error("Job entity not found in database: {}", jobId);
                // 释放资源
                resourceMonitorService.registerJobComplete(jobId, threadCount);
                userResourceLimitService.releaseUserResource(userId, threadCount);
                taskPriorityService.removeTask(jobId);
                return;
            }

            JobEntity jobEntity = jobEntityOpt.get();

            // 7. 更新JobEntity：标记为成功，填充结果，记录执行时间
            jobEntity.setStatus(JobStatus.SUCCEEDED);
            jobEntity.setUrlsSucceeded(succeeded);
            jobEntity.setUrlsFailed(failed);
            jobEntity.setResultHtml(resultHtml.toString());
            jobEntity.setExecutionTimeMs(executionTime);
            jobEntity.setCompletedAt(java.time.LocalDateTime.now());

            // 8. 保存到MySQL
            jobRepository.save(jobEntity);
            logger.info("Job completed successfully: jobId={}, succeeded={}, failed={}, executionTime={}ms", 
                    jobId, succeeded, failed, executionTime);

            // 9. 清理Redis实时状态（任务已完成，不再需要实时状态）
            String liveStatusKey = RedisKeyConstants.buildLiveStatusKey(jobId);
            redisTemplate.delete(liveStatusKey);
            logger.debug("Cleaned up live status in Redis for jobId: {}", jobId);

            // 10. 释放资源
            resourceMonitorService.registerJobComplete(jobId, threadCount);
            userResourceLimitService.releaseUserResource(userId, threadCount);
            taskPriorityService.removeTask(jobId);

        } catch (Exception e) {
            logger.error("Error processing crawler task: {}", jobId, e);
            // 更新状态为失败
            updateJobStatusToFailed(jobId, userId, threadCount);
        }
    }

    /**
     * 爬取单个URL
     * 
     * @param url 要爬取的URL
     * @return 解析后的HTML内容（清理后的）
     * @throws Exception 如果爬取失败
     */
    private String crawlUrl(String url) throws Exception {
        HttpGet httpGet = new HttpGet(url);
        httpGet.setHeader("User-Agent", USER_AGENT);
        httpGet.setHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        httpGet.setHeader("Accept-Language", "en-US,en;q=0.5");
        httpGet.setHeader("Accept-Encoding", "gzip, deflate");
        httpGet.setHeader("Connection", "keep-alive");

        try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
            int statusCode = response.getCode();
            
            if (statusCode != 200) {
                throw new RuntimeException("HTTP request failed with status code: " + statusCode);
            }

            // 读取响应内容
            String htmlContent = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            
            // 使用Jsoup解析HTML
            Document doc = Jsoup.parse(htmlContent, url);
            
            // 清理HTML：移除script和style标签
            doc.select("script, style").remove();
            
            // 获取body内容（或整个文档）
            Element body = doc.body();
            if (body != null) {
                return body.html();
            } else {
                return doc.html();
            }
        }
    }

    /**
     * 更新Redis中的实时状态
     */
    private void updateLiveStatus(String jobId, JobStatus status, int urlsSubmitted,
                                 int urlsSucceeded, int urlsFailed, String message) {
        try {
            Map<String, Object> liveStatus = new HashMap<>();
            liveStatus.put("status", status.name());
            liveStatus.put("message", message);
            liveStatus.put("urlsSubmitted", urlsSubmitted);
            liveStatus.put("urlsSucceeded", urlsSucceeded);
            liveStatus.put("urlsFailed", urlsFailed);

            String liveStatusJson = objectMapper.writeValueAsString(liveStatus);
            String liveStatusKey = RedisKeyConstants.buildLiveStatusKey(jobId);

            // 写入Redis，设置TTL为1小时（防止Worker崩溃导致状态残留）
            redisTemplate.opsForValue().set(liveStatusKey, liveStatusJson,
                    java.time.Duration.ofHours(1));

            logger.debug("Updated live status in Redis for jobId: {}", jobId);
        } catch (JsonProcessingException e) {
            logger.error("Failed to update live status for jobId: {}", jobId, e);
        }
    }

    /**
     * 更新任务状态为失败
     */
    private void updateJobStatusToFailed(String jobId, String userId, int threadCount) {
        try {
            Optional<JobEntity> jobEntityOpt = jobRepository.findById(jobId);
            if (jobEntityOpt.isPresent()) {
                JobEntity jobEntity = jobEntityOpt.get();
                jobEntity.setStatus(JobStatus.FAILED);
                jobEntity.setCompletedAt(java.time.LocalDateTime.now());
                jobRepository.save(jobEntity);

                // 清理Redis状态
                String liveStatusKey = RedisKeyConstants.buildLiveStatusKey(jobId);
                redisTemplate.delete(liveStatusKey);
            }

            // 释放资源
            resourceMonitorService.registerJobComplete(jobId, threadCount);
            userResourceLimitService.releaseUserResource(userId, threadCount);
            taskPriorityService.removeTask(jobId);
        } catch (Exception e) {
            logger.error("Failed to update job status to FAILED for jobId: {}", jobId, e);
        }
    }

    /**
     * HTML转义工具方法
     */
    private String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                  .replace("<", "&lt;")
                  .replace(">", "&gt;")
                  .replace("\"", "&quot;")
                  .replace("'", "&#39;");
    }
}

