# Prompt Hub

> 多租户 AI Prompt 管理与计费 SaaS 引擎

Prompt Hub 是一个面向 SaaS 场景的 **Prompt 模板管理 + 按次扣费 + AI 调用** 系统。项目已完成从 Django 到 Spring Boot 的架构重构，并配套集成测试与并发扣费验证体系。

## 项目亮点

- **多租户隔离**：基于 Sa-Token + MyBatis 拦截器，实现租户级数据隔离与权限控制
- **Prompt 模板引擎**：支持模板变量（如 `{{product}}`）、定价、启用/停用管理
- **租户积分体系**：每个租户独立余额，调用成功后自动扣费
- **并发安全扣费**：乐观锁 + 重试机制，防止高并发场景下的超卖与丢失更新
- **AI 调用集成**：对接 Google Gemini API，支持同步调用与 SSE 流式输出
- **完整管理后台**：基于 RuoYi 二次开发，提供用户、角色、菜单、监控等基础能力

## 技术栈

| 层级 | 技术 |
|------|------|
| 前端 | Vue 2、Element UI、Axios |
| 后端 | Spring Boot 2.6、MyBatis、Druid |
| 认证鉴权 | Sa-Token（JWT 模式） |
| 数据库 | MySQL 8.x |
| 缓存 | Redis |
| AI | Google Gemini API |
| 接口文档 | Knife4j / Swagger |

## 项目结构

```
prompt_hub/
├── Springboot-Multi-Tenant-SaToken/
│   ├── ruoyi/                      # 后端服务（Spring Boot）
│   │   ├── src/main/java/          # 业务代码
│   │   ├── src/main/resources/     # 配置文件
│   │   ├── src/test/java/          # 集成测试 & 并发压测用例
│   │   └── sql/multi_tenant.sql    # 数据库初始化脚本
│   └── prompt-hub-frontend/        # 前端管理界面（Vue）
├── update_pwd.sql                  # 租户管理员密码重置脚本
└── port_status.txt                 # 服务端口状态记录
```

## 核心功能

### 1. Prompt 模板管理

租户可创建、编辑 Prompt 模板，配置：

- 模板标题与内容
- 变量占位符（如 `{{product}}`、`{{audience}}`）
- 单次调用扣费积分
- 启用 / 停用状态

### 2. 租户余额管理

- 每个租户拥有独立积分账户
- 调用 AI 前校验余额是否充足
- 调用成功后按模板定价扣费
- 扣费失败时自动重试，避免并发冲突

### 3. Prompt 运行引擎

提供 REST API 与 SSE 流式接口：

| 接口 | 方法 | 说明 |
|------|------|------|
| `/prompt/run` | POST | 同步调用，返回完整 AI 回复 |
| `/prompt/run/stream` | POST | SSE 流式输出，逐块推送生成内容 |

**请求示例：**

```json
{
  "templateId": "tpl_adcopy",
  "inputText": "根据变量生成高转化投放文案",
  "vars": {
    "product": "智能台灯",
    "audience": "学生与办公人群",
    "USP": "护眼无频闪",
    "style": "专业可信"
  }
}
```

**响应示例：**

```json
{
  "code": 200,
  "msg": "这是高转化成品文案",
  "data": {
    "result": "这是高转化成品文案",
    "balanceInfo": {
      "balance": 14,
      "updateTime": "2026-06-07T12:00:00"
    }
  }
}
```

### 4. 多租户架构

- 登录态携带租户 ID（`comId`），所有业务查询自动过滤
- Sa-Token 独立 Redis 库，权限缓存与业务缓存分离
- 支持租户管理员、系统管理员等多角色权限体系

## 快速开始

### 环境要求

- JDK 1.8+
- Maven 3.6+
- Node.js 14+（前端开发）
- MySQL 8.x
- Redis 6.x

### 1. 初始化数据库

```bash
# 创建数据库并导入初始化脚本
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS mtt DEFAULT CHARSET utf8mb4;"
mysql -u root -p mtt < Springboot-Multi-Tenant-SaToken/ruoyi/sql/multi_tenant.sql
```

如需重置租户管理员密码，可执行根目录下的 `update_pwd.sql`。

### 2. 配置后端

编辑 `Springboot-Multi-Tenant-SaToken/ruoyi/src/main/resources/application-dev.properties`：

```properties
# 数据库
spring.datasource.druid.master.url=jdbc:mysql://127.0.0.1:3306/mtt?...
spring.datasource.druid.master.username=root
spring.datasource.druid.master.password=your_password

# Redis
spring.redis.host=127.0.0.1
spring.redis.port=6379

# Gemini AI（建议使用环境变量 GEMINI_API_KEY）
gemini.api-key=your_gemini_api_key
gemini.proxy.enabled=true
gemini.proxy.host=127.0.0.1
gemini.proxy.port=7890
```

> **安全提示**：请勿将 API Key 提交到公开仓库，生产环境请通过环境变量或密钥管理服务注入。

### 3. 启动后端

```bash
cd Springboot-Multi-Tenant-SaToken/ruoyi
mvn spring-boot:run
```

后端默认运行在 **http://localhost:8089**

### 4. 启动前端

```bash
cd Springboot-Multi-Tenant-SaToken/prompt-hub-frontend
npm install
npm run dev
```

前端默认运行在 **http://localhost:8080**，API 请求代理至后端 8089 端口。

### 5. 运行测试

```bash
cd Springboot-Multi-Tenant-SaToken/ruoyi
mvn test
```

测试覆盖：

- Prompt 运行接口的成功 / 失败 / 空响应场景
- Gemini API 响应解析与代理配置
- **10 线程并发扣费**：验证乐观锁无丢失更新
- 部分失败场景下的精确扣费（如 10 次请求 7 成功 3 失败，余额扣减 42 而非 60）

## 架构说明

```
┌─────────────┐     ┌──────────────┐     ┌─────────────┐
│  Vue 前端   │────▶│ Spring Boot  │────▶│   MySQL     │
│  (8080)     │     │   (8089)     │     │  (多租户)   │
└─────────────┘     └──────┬───────┘     └─────────────┘
                           │
                    ┌──────┴───────┐
                    │              │
              ┌─────▼─────┐  ┌─────▼─────┐
              │   Redis   │  │  Gemini   │
              │ Sa-Token  │  │    API    │
              └───────────┘  └───────────┘
```

**调用流程：**

1. 用户登录 → Sa-Token 签发 JWT，Session 绑定租户 ID
2. 选择 Prompt 模板，填写变量 → 调用 `/prompt/run`
3. 后端校验模板状态与租户余额
4. 调用 Gemini API 生成内容
5. 成功后乐观锁扣费，返回结果与剩余余额

## 默认端口

| 服务 | 端口 |
|------|------|
| 前端 | 8080 |
| 后端 | 8089 |
| MySQL | 3306 |
| Redis | 6379 |

## 相关文档

- 子目录详细说明：[Springboot-Multi-Tenant-SaToken/README.md](Springboot-Multi-Tenant-SaToken/README.md)

## License

MIT
