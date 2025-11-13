package com.example.crawler.repository;

import com.example.crawler.entity.JobEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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
}

