package com.example.crawler.repository;

import com.example.crawler.entity.JobEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 爬虫任务数据访问层
 * 
 * <p>提供对JobEntity的CRUD操作。
 * 继承Spring Data JPA的JpaRepository，自动提供基本的数据库操作方法。
 * </p>
 */
@Repository
public interface JobRepository extends JpaRepository<JobEntity, String> {

    /**
     * 根据任务ID查找任务实体
     * 
     * @param jobId 任务唯一标识符
     * @return Optional包装的JobEntity，如果不存在则返回empty
     */
    Optional<JobEntity> findById(String jobId);

    /**
     * 查询最近完成的任务，用于资源评估
     * 
     * @param userId 用户ID（可选，如果为null则查询所有用户）
     * @param limit 查询数量限制
     * @return 返回[executionTimeMs, urlsSubmitted]的数组列表
     */
    @Query(value = "SELECT j.execution_time_ms, j.urls_submitted FROM job j " +
           "WHERE j.status = 'SUCCEEDED' " +
           "AND j.execution_time_ms IS NOT NULL " +
           "AND j.execution_time_ms > 0 " +
           "AND (:userId IS NULL OR j.user_id = :userId) " +
           "ORDER BY j.completed_at DESC " +
           "LIMIT :limit", nativeQuery = true)
    List<Object[]> findRecentCompletedJobs(@Param("userId") String userId, @Param("limit") int limit);
}

