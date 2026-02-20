# Prompt Hub 管理系统

## 简介

Prompt Hub 是一个基于 SpringBoot + Vue + SaToken 架构的多租户 Prompt 管理与扣费系统。
系统提供了多租户隔离、提示词模板管理、租户资产管理以及核心的扣费运行功能。

## 核心技术栈

* 前端：Vue.js, Element UI
* 后端：Spring Boot, MyBatis
* 权限与多租户：Sa-Token
* 数据库：MySQL

## 核心功能

1. **多租户架构**：底层基于 Sa-Token 和 MyBatis 拦截器实现严格的数据级多租户隔离。
2. **提示词管理**：支持维护提示词模板（标题、内容、扣除积分）。
3. **租户资产**：支持管理租户积分余额，带有乐观锁防止并发超卖。
4. **扣费调用引擎**：提供安全的计费拦截和 Mock 大模型回复接口。

