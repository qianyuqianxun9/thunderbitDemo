# 分布式爬虫服务 (Distributed Crawler Service)

## 项目简介

这是一个基于Spring Boot 3和Java 17的分布式爬虫服务后端API工程。该服务作为系统的"中心服务（Entrypoint）"，负责接收爬虫任务、管理任务状态，并提供结果查询接口。实际的爬虫任务由外部的Worker集群（如K8s部署）来完成。

## 核心架构

- **中心服务（Entrypoint）**：本Spring Boot应用，负责任务接收、状态管理和结果查询
- **Kafka消息队列**：作为任务队列，支持分布式处理和水平扩展
- **Kafka Consumer（Worker）**：从Kafka消费任务，执行真实的网页爬取逻辑
- **Redis**：实时状态缓存（任务队列已迁移到Kafka）
- **MySQL**：持久化存储任务最终状态和结果

## 技术栈

- Java 17
- Spring Boot 3.2.0
- Spring Data JPA
- Spring Data Redis
- Spring Kafka
- Apache Kafka
- Apache HttpClient 5（HTTP请求）
- Jsoup（HTML解析）
- MySQL 8.0+
- Maven

## 项目结构

```
src/main/java/com/example/crawler/
├── controller/          # API接入层
│   └── JobController.java
├── service/             # 核心业务逻辑层
│   ├── JobService.java
│   └── CrawlerConsumerService.java  # Kafka Consumer，执行真实爬虫任务
├── repository/          # 数据访问层
│   └── JobRepository.java
├── entity/             # JPA数据库实体
│   └── JobEntity.java
├── dto/                # 数据传输对象（Java 17 record）
│   ├── JobSubmitRequest.java
│   ├── JobSubmitResponse.java
│   └── JobStatusResponse.java
├── model/              # 业务模型
│   └── JobStatus.java
├── config/             # Spring配置类
│   ├── RedisConfig.java
│   ├── RedisKeyConstants.java
│   ├── KafkaConfig.java
│   └── KafkaConstants.java
├── exception/          # 自定义异常及全局异常处理器
│   ├── JobNotFoundException.java
│   ├── JobNotCompletedException.java
│   └── GlobalExceptionHandler.java
└── CrawlerApplication.java
```

## 核心API

### 1. 提交爬虫任务

**POST** `/api/v1/jobs`

**Request Body:**
```json
{
  "urls": [
    "https://example.com/page1",
    "https://example.com/page2"
  ]
}
```

**Response:**
```json
{
  "jobId": "550e8400-e29b-41d4-a716-446655440000"
}
```

### 2. 查询任务状态

**GET** `/api/v1/jobs/{jobId}/status`

**Response:**
```json
{
  "jobId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "RUNNING",
  "liveMessage": "Processing 50/100 URLs...",
  "urlsSubmitted": 100,
  "urlsSucceeded": 50,
  "urlsFailed": 0
}
```

**状态说明：**
- `PENDING`: 任务已提交，等待Worker处理
- `RUNNING`: Worker正在处理任务（此时会从Redis获取实时状态）
- `SUCCEEDED`: 任务成功完成
- `FAILED`: 任务执行失败

### 3. 获取任务结果

**GET** `/api/v1/jobs/{jobId}/result`

**Response:** HTML格式的文本内容

**Content-Type:** `text/html`

## 环境要求

- JDK 17+
- Maven 3.6+
- MySQL 8.0+
- Redis 6.0+（用于实时状态缓存）
- Apache Kafka 2.8+（用于任务队列）

## 配置说明

### application.yml

修改 `src/main/resources/application.yml` 中的数据库、Redis和Kafka配置：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/crawler_db
    username: root
    password: root
  
  data:
    redis:
      host: localhost
      port: 6379
  
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: crawler-worker-group
```

### 数据库初始化

创建数据库：

```sql
CREATE DATABASE crawler_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

JPA会自动创建表结构（`ddl-auto: update`）。

## 运行项目

1. **启动依赖服务**
   - 启动MySQL服务
   - 启动Redis服务
   - 启动Kafka服务（包括Zookeeper）

2. **创建Kafka主题（可选，应用会自动创建）**
   ```bash
   kafka-topics.sh --create --topic crawler-job-topic \
     --bootstrap-server localhost:9092 \
     --partitions 3 \
     --replication-factor 1
   ```

3. **编译项目**
   ```bash
   mvn clean compile
   ```

4. **运行应用**
   ```bash
   mvn spring-boot:run
   ```

5. **访问API**
   - 应用启动在 `http://localhost:8080`
   - API文档可通过Postman或curl测试

## 使用示例

### 1. 提交任务
```bash
curl -X POST http://localhost:8080/api/v1/jobs \
  -H "Content-Type: application/json" \
  -d '{
    "urls": [
      "https://example.com",
      "https://www.baidu.com"
    ]
  }'
```

### 2. 查询状态
```bash
curl http://localhost:8080/api/v1/jobs/{jobId}/status
```

### 3. 获取结果
```bash
curl http://localhost:8080/api/v1/jobs/{jobId}/result
```

## CrawlerConsumerService说明

`CrawlerConsumerService` 是Kafka消费者服务，执行真实的网页爬取任务：

### 核心功能

1. **从Kafka消费任务**：使用`@KafkaListener`监听`crawler-job-topic`主题
2. **真实爬虫实现**：
   - 使用Apache HttpClient 5发送HTTP请求
   - 使用Jsoup解析HTML内容
   - 清理HTML（移除script和style标签）
   - 支持自定义User-Agent和请求头
3. **实时状态更新**：处理过程中更新Redis实时状态
4. **结果聚合**：将所有URL的爬取结果聚合为HTML报告
5. **错误处理**：单个URL失败不影响其他URL，记录失败信息

### 爬虫特性

- **HTTP客户端**：使用Apache HttpClient 5，支持连接池和超时控制
- **HTML解析**：使用Jsoup解析和清理HTML内容
- **错误容错**：单个URL失败不影响整体任务
- **进度跟踪**：实时更新处理进度到Redis
- **结果格式**：生成格式化的HTML报告，包含成功和失败的URL详情

### 技术实现

- **Kafka Consumer**：手动提交offset，确保消息处理成功后才提交
- **事务支持**：使用`@Transactional`确保数据一致性
- **资源管理**：正确关闭HTTP连接和响应流

**注意：** 在实际生产环境中，Consumer可以是独立的服务（如K8s Pod），支持水平扩展。

## 设计亮点

1. **DDD分层架构**：严格按照领域驱动设计思想划分层次
2. **Kafka消息队列**：使用Kafka替代Redis队列，支持分布式处理和水平扩展
3. **真实爬虫实现**：使用HttpClient和Jsoup实现真实的网页爬取和解析
4. **状态合并机制**：智能合并Redis实时状态和MySQL持久化状态
5. **异步任务处理**：任务提交后立即返回，Consumer异步处理
6. **优雅的异常处理**：统一的全局异常处理器，返回规范的错误响应
7. **Java 17特性**：使用record类型定义DTO，代码更简洁
8. **完整的日志记录**：关键路径都有日志记录，便于排查问题
9. **手动Offset提交**：确保消息处理成功后才提交，避免消息丢失

## 错误处理

所有API错误都会返回统一的JSON格式：

```json
{
  "status": 404,
  "message": "Job not found",
  "details": "Job not found with id: xxx"
}
```

## 许可证

MIT License

