# PromptHub-MultiTenant

> 基于 RuoYi + Sa-Token 的企业级多租户管理平台二次开发项目

**仓库**：https://github.com/qunqing11/prompt_hub

---

## 一、项目定位

`PromptHub-MultiTenant` 是在国内主流开源脚手架 **RuoYi (若依)** 之上进行深度二次开发的企业级多租户管理平台。项目在保留若依成熟后台能力的基础上，融合 **Sa-Token** 权限框架，专注于多租户数据隔离、动态路由鉴权及 AI Prompt 计费引擎的落地。

| 问题域 | 解决方案 |
|--------|----------|
| 数据隔离 | 单库逻辑隔离（`com_id` 租户字段过滤）+ 多数据源物理隔离（动态数据源切换） |
| 权限控制 | Sa-Token JWT 无状态鉴权 + 租户上下文绑定 + 路由级拦截 |
| AI 业务对接 | Prompt 模板管理、按次扣费、Gemini API 同步 / SSE 流式调用 |
| 高并发稳定性 | Caffeine 二级缓存 + Redis Lua 分布式限流 + 乐观锁扣费 |

### 架构演进

项目经历了 **从轻量级 Django 业务架构向企业级 Spring Boot 生态的全面重构迁移**，并建立完整的高并发压测与调优体系（JMeter 场景脚本 + 集成测试 + Cursor Agent 辅助自动化）。

---

## 二、核心技术栈

| 层级 | 技术选型 |
|------|----------|
| 后端 | Spring Boot 2.6、Sa-Token、MyBatis-Plus、Druid |
| 缓存 | Redis + Caffeine 二级缓存 |
| 数据库 | MySQL 8.x |
| 前端 | Vue 2、Element UI |
| 高并发组件 | Redis + Lua 滑动窗口限流、Caffeine 租户路由缓存 |
| 压测 / 工程化 | JMeter、Cursor Agent |

---

## 三、核心贡献与重构要点

### 3.1 主导架构迁移（Django → Spring Boot）

- 梳理原 Django 业务域（租户、模板、余额、扣费），完成数据模型与分层架构对齐。
- 重写 Prompt 运行引擎：模板校验 → 余额预检 → AI 调用 → 成功后乐观锁扣费，失败不扣费。
- 建立集成测试矩阵，覆盖成功、失败、空响应及 10 线程并发扣费场景。

### 3.2 多租户数据隔离方案

- **逻辑隔离**：Mapper 层强制 `com_id` 过滤，Sa-Token Session 绑定租户上下文。
- **物理隔离**：`DynamicDataSource` + `@DataSource` AOP + `TenantDataSourceInterceptor` 请求入口路由。
- **路由缓存**：`TenantDataSourceRouter` 采用 Caffeine → Redis → 底层解析三级策略，降低高并发下路由解析开销。

### 3.3 高并发压测调优

| 组件 | 路径 | 说明 |
|------|------|------|
| `@TenantRateLimiter` | `framework.aspectj.lang.annotation` | 租户维度限流注解，支持 window / limit |
| `TenantRateLimiterAspect` | `framework.aspectj` | Redis + Lua 滑动窗口，Key 拼接 Tenant-ID |
| `TenantDataSourceRouter` | `framework.datasource` | Caffeine 本地缓存加速数据源 Key 解析 |
| `TenantDataSourceInterceptor` | `framework.interceptor` | 请求入口绑定租户数据源上下文 |

**限流 Key 格式**：`rate_limit:tenant:{comId}:{业务标识}`

**路由缓存 Key 格式**：Caffeine 内存 → Redis `tenant:ds:{comId}` → 默认 `MASTER`

---

## 四、项目结构

```
prompt_hub/
├── Springboot-Multi-Tenant-SaToken/
│   ├── ruoyi/                                    # 后端
│   │   ├── framework/
│   │   │   ├── aspectj/TenantRateLimiterAspect   # 分布式限流切面
│   │   │   ├── datasource/TenantDataSourceRouter # Caffeine 路由缓存
│   │   │   └── interceptor/TenantDataSourceInterceptor
│   │   ├── project/system/controller/          # Prompt 运行 / 模板 / 余额
│   │   └── sql/multi_tenant.sql
│   └── prompt-hub-frontend/                      # Vue 前端
└── README.md
```

---

## 五、核心 API

| 接口 | 方法 | 限流 | 说明 |
|------|------|------|------|
| `/prompt/run` | POST | 60s / 100次 | 同步调用 Prompt |
| `/prompt/run/stream` | POST | 60s / 60次 | SSE 流式输出 |

---

## 六、快速开始

### 6.1 环境要求

JDK 1.8+ · Maven 3.6+ · Node.js 14+ · MySQL 8.x · Redis 6.x

### 6.2 初始化数据库

```bash
mysql -u <DB_USER> -p -e "CREATE DATABASE IF NOT EXISTS mtt DEFAULT CHARSET utf8mb4;"
mysql -u <DB_USER> -p mtt < Springboot-Multi-Tenant-SaToken/ruoyi/sql/multi_tenant.sql
```

### 6.3 环境变量配置（必填）

> 所有敏感配置通过环境变量注入，**禁止写入配置文件并提交至 Git**。

| 环境变量 | 必填 | 说明 |
|----------|:----:|------|
| `SPRING_DATASOURCE_URL` | ✅ | JDBC 连接串 |
| `SPRING_DATASOURCE_USERNAME` | ✅ | 数据库用户名 |
| `SPRING_DATASOURCE_PASSWORD` | ✅ | 数据库密码 |
| `SPRING_REDIS_HOST` | ✅ | Redis 主机 |
| `SPRING_REDIS_PORT` | ✅ | Redis 端口 |
| `SPRING_REDIS_PASSWORD` | — | Redis 密码 |
| `SA_TOKEN_JWT_SECRET_KEY` | ✅ | Sa-Token JWT 签名密钥 |
| `GEMINI_API_KEY` | ✅ | Gemini API 密钥 |
| `GEMINI_PROXY_ENABLED` | — | 是否启用代理，默认 `false` |

**Linux / macOS 示例**：

```bash
export SPRING_DATASOURCE_URL="jdbc:mysql://127.0.0.1:3306/mtt?useUnicode=true&characterEncoding=utf8&serverTimezone=GMT%2B8"
export SPRING_DATASOURCE_USERNAME="<your_db_user>"
export SPRING_DATASOURCE_PASSWORD="<your_db_password>"
export SPRING_REDIS_HOST="127.0.0.1"
export SPRING_REDIS_PORT="6379"
export SA_TOKEN_JWT_SECRET_KEY="<your_jwt_secret>"
export GEMINI_API_KEY="<your_gemini_api_key>"
```

**Windows PowerShell 示例**：

```powershell
$env:SPRING_DATASOURCE_URL = "jdbc:mysql://127.0.0.1:3306/mtt?useUnicode=true&characterEncoding=utf8&serverTimezone=GMT%2B8"
$env:SPRING_DATASOURCE_USERNAME = "<your_db_user>"
$env:SPRING_DATASOURCE_PASSWORD = "<your_db_password>"
$env:SPRING_REDIS_HOST = "127.0.0.1"
$env:SPRING_REDIS_PORT = "6379"
$env:SA_TOKEN_JWT_SECRET_KEY = "<your_jwt_secret>"
$env:GEMINI_API_KEY = "<your_gemini_api_key>"
```

### 6.4 启动服务

```bash
# 后端（8089）
cd Springboot-Multi-Tenant-SaToken/ruoyi
mvn spring-boot:run

# 前端（8080）
cd Springboot-Multi-Tenant-SaToken/prompt-hub-frontend
npm install && npm run dev
```

### 6.5 运行测试

```bash
cd Springboot-Multi-Tenant-SaToken/ruoyi
mvn test
```

---

## 七、系统架构

```
┌──────────────┐      ┌──────────────────────────────────────────┐
│  Vue 前端     │─────▶│  Spring Boot 后端                         │
│  (8080)      │      │  Sa-Token · 租户路由 · 限流 · 扣费引擎      │
└──────────────┘      └──────┬───────────────┬──────────┬─────────┘
                             │               │          │
                      ┌──────▼─────┐  ┌──────▼────┐ ┌──▼────────┐
                      │   MySQL    │  │   Redis   │ │ Caffeine  │
                      │  多租户     │  │ Lua 限流  │ │ 路由缓存   │
                      └────────────┘  └───────────┘ └───────────┘
                                              │
                                       ┌──────▼──────┐
                                       │ Gemini API  │
                                       └─────────────┘
```

---

## License

MIT · 基于 [RuoYi](http://www.ruoyi.vip) 二次开发
