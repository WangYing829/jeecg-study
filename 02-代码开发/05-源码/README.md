# 源码目录总览

> 本文件是 **`05-源码/` 的唯一基座信息入口**：上游是谁、锁定哪个版本、改了什么、代码放哪。
> 各子目录的 README 只讲**自己那摊**的落点与约束，不再重复基座信息表（避免三处各写一份、越改越不一致）。

## 1 基座信息（唯一来源）

| 项 | 值 |
|---|---|
| 上游 | JeecgBoot 官方仓库 |
| 版本 | **tag `v3.9.2`**，commit `7df07a8`（锁定；禁止无 tag 的 3.9.3） |
| 后端路径 | `01-JeecgBoot基座/jeecg-boot/` |
| 前端路径 | `01-JeecgBoot基座/jeecgboot-vue3/` |
| 工作分支 | `feature/jeecg-study`（基于 v3.9.2，承载本项目全部改造） |
| 官方 AI 规范 | [`01-JeecgBoot基座/jeecg-boot/CLAUDE.md`](01-JeecgBoot基座/jeecg-boot/CLAUDE.md) —— **写码前必读** |
| 本项目版速查 | [`../../06-AI协作/04-知识库/01-JeecgBoot底座速查.md`](../../06-AI协作/04-知识库/01-JeecgBoot底座速查.md) —— 每条带 `file:line` 证据 |

> 上游代码**尽量不动**。必须改时用官方约定的 `update-begin` / `update-end` 注释痕迹包住，便于日后与上游合并。

## 2 本目录结构

| 目录 | 装什么 | 说明 |
|---|---|---|
| `01-JeecgBoot基座/` | **真实源码 + 落点说明** | 上游 JeecgBoot v3.9.2 独立基座，自带 `.git`（分支 `feature/jeecg-study`）；根 `README.md` 末尾的「jeecg-study 项目落点说明」章节是后端 + 前端落点说明（原 `01-后端/`、`02-前端/` 已合并进来） |
| `03-构建发布/` | 构建与发布说明 | 端口规划 · 部署形态 · 产物边界 · 发布检查 |
| `04-建库迁移/` | `migrate.ps1` | 建库 + 迁移执行器（幂等，按 `study_schema_migration` 跳过已执行脚本） |

## 3 本项目对上游的改动（唯一清单）

| # | 改哪 | 改什么 | 状态 |
|---|---|---|---|
| 1 | `application-dev.yml` / `-test` / `-prod` / `-docker.yml` | JDBC URL 库名 → `jeecg-study` | ✅ 已改 |
| 2 | `application-prod.yml` | 数据库口令 → `${DB_PASSWORD}` 环境变量 | ✅ 已改 |
| 3 | `jeecg-boot/docker-compose.yml` | 新增 `MYSQL_DATABASE: jeecg-study` | ✅ 已改 |
| 4 | `jeecg-boot/db/Dockerfile` | 初始化脚本指向 `jeecg-study-mysql.sql` | ✅ 已改 |
| 5 | `jeecg-boot-module/pom.xml` | `<modules>` 注册 `jeecg-module-study` | ✅ 已改 |
| 6 | `jeecg-system-start/pom.xml` | 依赖 `jeecg-module-study` | ✅ 已改 |
| 7 | `jeecg-boot/.gitignore` | 排除两个生成产物（docker 派生产物 / 运行时释放目录） | ✅ 已改 |
| 8 | `jeecgboot-vue3/.env` / `.env.development` / `.env.docker` / `.env.dockercloud` | `VITE_GLOB_ICONIFY_USE_TYPE=local`（离线图标集） | ✅ 已改 |
| 9 | `jeecgboot-vue3/index.html` | 标题 / 品牌 | ✅ 已改 |
| 10 | `jeecgboot-vue3/src/router/routes/modules/about.ts` | 图标名修正（`simple-icons:aboutdotme`） | ✅ 已改 |
| 11 | `jeecg-boot/jeecg-boot-module/jeecg-module-study/` | **新增**：本项目业务模块（域优先分包，ADR-016） | ✅ 已加 |
| 12 | `jeecgboot-vue3/src/{views/study,components/study,layouts/study}/` | **新增**：学习端 / 家长端页面与组件 | ✅ 已加 |
| 13 | `jeecgboot-vue3/src/components/Icon/data/menu-icons.ts` | **新增**：数据库菜单图标白名单（ADR-034） | ✅ 已加 |

> 上游 `jeecg-boot/db/jeecgboot-mysql-5.7.sql` **保持不动**——需要新库名版本时跑
> `05-公共资源/2026103018-平台库脚本.py` 重新生成，不要手改上游文件。

## 4 基座入库策略（重要）

上游代码自带 `.git`，是独立 Git 仓库。**不要**把它连同上游 `.git` 一起提交进本仓库历史；
本仓库**只跟踪本项目的改动**（相对上游 v3.9.2 的**修改 / 新增 / 删除**），上游历史留在基座自己的 `.git` 里。

| 动作 | 命令 / 工具 |
|---|---|
| 算出相对上游的改动清单 | `git -C <基座> diff --name-only v3.9.2` + `git -C <基座> ls-files --others --exclude-standard` |
| 导出 diff 清单 | `python 05-公共资源/2026103019-基座同步.py export` |
| **把 diff 精确写进本仓库索引** | `python 05-公共资源/2026103019-基座同步.py stage` |
| 从上游还原完整基座（clone 后） | `python 05-公共资源/2026103019-基座同步.py restore --target <目录>` |

### ⚠ 为什么不能用 `git add -f`

基座目录自带 `.git`，在父仓库眼里就是一个**嵌套仓库**。实测：

| 命令 | 结果 |
|---|---|
| `git add -f -- <基座内文件>` | **exit 0 但索引没变**（静默跳过） |
| `git add -f -- <整个基座目录>` | `fatal: 'xxx/' does not have a commit checked out` |
| `git update-index --add --index-info`（批量形式） | 被 `.gitignore` 命中而**静默跳过**（exit 0 但条目没落地） |
| `git update-index --add --cacheinfo`（单条形式） | ✅ **可用**——绕开工作区扫描与 ignore 规则 |

`stage` 子命令就是用最后这一条路径（`hash-object -w --no-filters` + `update-index --cacheinfo`，分批 80 条），
并**回读校验** `git ls-files --cached` 确认条目真的落地。

> **通用教训**：**「命令 exit 0」不等于「事情做成了」**。批量写索引 / 批量写文件之后一律要回读校验。

### 凭据

基座里的口令**一律走环境变量**（`${DB_PASSWORD:root}` / `${MYSQL_ROOT_PASSWORD:-root}`），
真实口令不写入任何入库文件。入库前跑 `python 05-公共资源/2026103023-凭据扫描.py`。

## 5 子目录导航

| 子目录 | 什么时候看 |
|---|---|
| [`01-JeecgBoot基座/README.md`](01-JeecgBoot基座/README.md) 的「jeecg-study 项目落点说明」 | 写 Java / Vue 代码前：包结构、实体注解、`Result<T>`、编码约束、四套形态、护眼实现、页面清单对照 |
| [`03-构建发布/README.md`](03-构建发布/README.md) | 构建 / 部署 / 发布前：端口、产物边界、发布检查 |
| [`../../06-AI协作/01-任务台账/2026103019-多用户改造契约.md`](../../06-AI协作/01-任务台账/2026103019-多用户改造契约.md) | 动多用户相关代码前（身份 / 4 表 / 21 接口的唯一依据） |
| [`../../06-AI协作/03-决策记录/01-架构决策记录.md`](../../06-AI协作/03-决策记录/01-架构决策记录.md) | 拿不准某个设计取舍时（每条 ADR 都带复审触发条件） |
