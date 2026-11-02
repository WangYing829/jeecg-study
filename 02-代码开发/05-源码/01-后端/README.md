# 后端源码

> **基座已就位**——真实源码在 [`../01-JeecgBoot基座/jeecg-boot/`](../01-JeecgBoot基座/jeecg-boot/)。
> 本文件说明**代码放在哪、写码时必须守什么**。

## 1 基座信息

基座信息（上游 / 版本 / tag / 分支 / 本地路径 / 官方规范）**统一在 [`../README.md`](../README.md)**，本文件不重复维护。

> 上游代码**尽量不动**。必须改时用官方约定的 `update-begin` / `update-end` 注释痕迹包住，便于日后与上游合并。
> 官方 AI 规范见 [`../01-JeecgBoot基座/jeecg-boot/CLAUDE.md`](../01-JeecgBoot基座/jeecg-boot/CLAUDE.md) —— **写码前必读**。

## 2 代码放在哪

| 组件 | 落地位置 | 状态 |
|---|---|---|
| 业务模块 | `jeecg-boot/jeecg-boot-module/jeecg-module-study/` | ✅ `pom.xml` 已建并注册 |
| 官方参考实现 | `jeecg-boot/jeecg-boot-module/jeecg-module-monitor/` | ✅ 只读参考，**不改** |
| 数据源配置 | `jeecg-module-system/jeecg-system-start/src/main/resources/application-{dev,test,prod,docker}.yml` | ✅ 库名已改 `jeecg-study` |
| 平台库脚本 | `jeecg-boot/db/jeecg-study-mysql.sql` | ✅ 133 表 / 7446 条种子 |
| 业务库脚本真源 | `../../02-数据库/03-脚本/` | ✅ 17 张表 DDL |

### 包结构（域优先，ADR-016）

```
org.jeecg.modules.study.<业务域>/
├── controller/     # 只做参数校验、鉴权、DTO↔VO
├── entity/         # MyBatis-Plus 实体
├── mapper/
├── service/
│   └── impl/
└── vo/             # 出入参
```

> **不要**建 `jeecg-module-study-api` 两模块拆分（ADR-016 已调整为单模块）。
> **不要**按技术层分 `controller/service/dao`（那是旧口径，ADR-014 已关闭）。

## 3 基座已改了什么（本项目对上游的改动）

**统一清单在 [`../README.md` §3](../README.md)**（后端 + 前端 + 打包配置 + 新增模块，13 条），本文件不重复维护。

其中与后端直接相关的：数据源配置（库名 → `jeecg-study`、口令 → 环境变量）、`jeecg-boot-module/pom.xml` 注册模块、`jeecg-system-start/pom.xml` 加依赖、`jeecg-module-study/` 整个业务模块为新增。

> 上游 `jeecg-boot/db/jeecgboot-mysql-5.7.sql` **保持不动**——需要新库名版本时跑 `05-公共资源/2026103018-平台库脚本.py` 重新生成，不要手改上游文件。

## 4 编码约束速查

| 约束 | 说明 |
|---|---|
| JeecgBoot **3.9.2** | 禁止无 tag 的 3.9.3 |
| **单库** | 平台表 + 业务表同在 `jeecg-study`，**不要用 `@DS("study")`**（ADR-015） |
| **单模块** | 只有 `jeecg-module-study` 一个（ADR-016） |
| 表前缀 `study_` | API 根 `/api/v1/study`，权限码 `study:<资源>:<动作>` |
| Controller 不写业务逻辑 | 只做参数校验、鉴权、DTO↔VO |
| Engine 纯计算 | 不碰 DB、不碰网络；`rule` / `engine.judge` 包**零 AI 依赖**（ArchUnit 断言） |
| 事务边界在 Service | 错题池 / 排期 / 掌握度同事务 |
| **数据范围隔离** | 家长只见 `study_parent_child_rel` 关联的孩子；孩子只见自己。Service 层强制拼 `child_id`；**越权返 4010（不用 404）** |
| `child_id` 可空 | 存量数据允许 NULL（新数据必填），应用层对已登录用户强制补条件 |
| 不做多租户 | 不引入租户字段、不做通用权限体系 |

## 5 写码前必读

| 文档 | 用途 |
|---|---|
| [`../01-JeecgBoot基座/jeecg-boot/CLAUDE.md`](../01-JeecgBoot基座/jeecg-boot/CLAUDE.md) | 上游官方规范：包结构、实体注解、`Result<T>`、痕迹约定 |
| [`../../../../06-AI协作/04-知识库/01-JeecgBoot底座速查.md`](../../../../06-AI协作/04-知识库/01-JeecgBoot底座速查.md) | 本项目版速查，**每条带 file:line 证据** |
| [`../../../../06-AI协作/01-任务台账/2026103019-多用户改造契约.md`](../../../../06-AI协作/01-任务台账/2026103019-多用户改造契约.md) | 多用户改造契约（身份 / 4 表 / 21 接口） |
| [`../../01-设计文档/01-系统设计说明书.html`](../../01-设计文档/01-系统设计说明书.html) | 模块划分、包结构、状态机、认证栈 |

## 6 什么时候需要动这个目录

| 场景 | 动作 |
|---|---|
| 写业务代码 | 在 `jeecg-module-study` 下按域优先建包，**先更新系统设计说明书**再落地 |
| 只改设计 | **不动本目录**，改 `../../01-设计文档/` 的源稿后重新生成 HTML |
| 升级基座 | 先看上游 Release 是否存在 `v3.9.x` tag；升级要在 `feature/jeecg-study` 上做，保留本地改动 |
| 加模块 | 先在设计说明书补包结构，再落地 |
