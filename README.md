# AI 虚拟养宠游戏平台

基于 Spring Cloud 微服务架构的 AI 虚拟养宠游戏平台，业务上分为 **宠物、商城、分析、社交、AI** 五大模块，并配有独立的认证服务与 API 网关。

## 技术栈

- JDK 17 / Spring Boot 3.3 / Spring Cloud 2023.0.1 / Spring Cloud Alibaba 2023.0.1.0
- Nacos（注册中心 + 配置中心）、Spring Cloud Gateway、OpenFeign
- MySQL + MyBatis-Plus、Redis + Redisson
- LangChain4j（AI 能力）、JWT、Knife4j（接口文档）

## 模块结构

| 模块 | 端口 | 说明 | 数据库 |
| --- | --- | --- | --- |
| gateway | 8080 | API 网关，统一路由与鉴权 | - |
| auth-service | 8086 | 用户认证、登录注册 | cyber_user |
| pet-service | 8081 | 宠物养成 | cyber_pet |
| shop-service | 8082 | 商城、钱包、背包 | cyber_shop |
| social-service | 8083 | 社交、帖子、互动 | cyber_social |
| analytics-service | 8085 | 数据分析、周报 | cyber_analytics |
| agent-service | 8084 | AI 模块（LangChain4j） | - |
| doc-aggregator | 8088 | 聚合 API 文档（Knife4j） | - |
| common-api | - | 公共接口 / DTO | - |
| common-util | - | 公共工具类 | - |

## 环境要求

- JDK 17、Maven 3.6+
- MySQL 8.x、Redis、Nacos（默认地址 `192.168.111.129:8848`）

## 数据库初始化

执行根目录下的 `script.sql`，会创建并初始化 5 个业务库：

```
cyber_user / cyber_pet / cyber_shop / cyber_social / cyber_analytics
```

数据库/Redis 连接地址通过 `${cyber.db.host}`、`${cyber.redis.host}` 等占位符配置（可改 Nacos 配置或本地 profile）。

## 启动方式

1. 先启动 Nacos、MySQL、Redis。
2. 在根目录执行打包：

```bash
mvn clean install -DskipTests
```

3. 依次启动各服务（或直接运行各模块的 `Application` 主类），建议顺序：

```bash
auth-service → pet-service → shop-service → social-service
→ analytics-service → agent-service → gateway
```

4. 启动完成后，通过网关统一访问，例如：

```
http://localhost:8080/pet/pet-info/...
http://localhost:8080/shop/...
```

## 接口文档

启动 `doc-aggregator` 后访问 Knife4j 聚合文档：

```
http://localhost:8088/doc.html
```

## 目录约定

- `src/main/java` —— 业务代码（controller / service / mapper / entity 等）
- `src/main/resources/application.yaml` —— 基础配置
- `src/main/resources/application-local.yaml` / `application-dev.yaml` —— 环境配置（`local` 为默认 profile）
