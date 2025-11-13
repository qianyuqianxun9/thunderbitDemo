package com.example.crawler.config;

/**
 * Redis键常量定义
 * 
 * <p>统一管理Redis中使用的所有键名，避免硬编码和键名冲突。
 * 注意：任务队列已迁移到Kafka，Redis仅用于存储实时状态缓存。
 * </p>
 */
public final class RedisKeyConstants {

    private RedisKeyConstants() {
        // 工具类，禁止实例化
    }

    /**
     * 任务实时状态键名前缀
     * 
     * <p>用于存储Worker上报的实时状态信息。
     * 完整键名格式：scraping:job:live:status:{jobId}
     * 
     * 例如：scraping:job:live:status:550e8400-e29b-41d4-a716-446655440000
     * </p>
     */
    public static final String JOB_LIVE_STATUS_PREFIX = "scraping:job:live:status:";

    /**
     * 构建任务实时状态的完整Redis键名
     * 
     * @param jobId 任务ID
     * @return 完整的Redis键名
     */
    public static String buildLiveStatusKey(String jobId) {
        return JOB_LIVE_STATUS_PREFIX + jobId;
    }
}

