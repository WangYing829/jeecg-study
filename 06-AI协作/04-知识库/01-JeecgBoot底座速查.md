# JeecgBoot 3.9.2 底座速查

> **写 jeecg-study 代码前必读本文件。** 每条写法都标了 `file:line`，**照抄出处，不要凭记忆写 JeecgBoot**。
> 证据来源：**本项目自己的独立基座** `02-代码开发/05-源码/01-JeecgBoot基座/`（tag `v3.9.2`，commit `7df07a8`，分支 `feature/jeecg-study`）。
> 路径记法：`<Boot>` = 上面这个路径的 `jeecg-boot/`。

---

## 0 官方规范就在基座里

> ⚠️ **别自己发明规范。** JeecgBoot 3.9.2 自带 AI 编码指引：

```
<Boot>/CLAUDE.md
```

| 它规定了什么 | 原文要点 |
|---|---|
| **包结构** | `org.jeecg.modules.<module-name>.{controller,entity,mapper,mapper.xml,service,service.impl,vo}` —— **域优先**，层在域内 |
| **Controller** | `<Entity>Controller extends JeecgController<Entity, IService>`（基类自带标准 CRUD + Excel 导入导出） |
| **Service** | 接口 `I<Entity>Service extends IService<Entity>`，实现 `<Entity>ServiceImpl extends ServiceImpl<Mapper, Entity>` |
| **Mapper** | `<Entity>Mapper extends BaseMapper<Entity>`，XML 放 `mapper/xml/` |
| **实体注解** | `@Data` `@EqualsAndHashCode(callSuper = false)` `@Accessors(chain = true)` `@TableName` `@TableId(type = IdType.ASSIGN_ID)` |
| **响应包装** | `Result<T>`（`org.jeecg.common.api.vo.Result`），用 `Result.OK(data)` / `Result.OK(msg, data)` / `Result.error(msg)` |
| **代码修改痕迹** | **所有新增或修改的代码块必须用 `update-begin` / `update-end` 注释包裹**，格式见 §6 |
| **建库** | 导入 `db/jeecgboot-mysql-5.7.sql`；增量用 Flyway（脚本在 `jeecg-system-start/src/main/resources/flyway/sql/mysql/`） |
| **端口** | 8080，context-path `/jeecg-boot` |

> **本项目已按域优先分包**（ADR-014 已关闭）。`study/family/controller` 这种写法是**上游官方规范**，不是我们的发明。

---

## 1 依赖与版本

| 项 | 值 | 出处 |
|---|---|---|
| 动态数据源 | `com.baomidou:dynamic-datasource-spring-boot3-starter:4.3.1` | `<Boot>/jeecg-boot-base-core/pom.xml:128-133` |
| Druid | `com.alibaba:druid-spring-boot-3-starter:1.2.24` | `jeecg-boot-base-core/pom.xml:121-126` |
| MyBatis-Plus | `3.5.12` | `<Boot>/jeecg-boot/pom.xml:64` |
| 版本属性 | `<dynamic-datasource-spring-boot-starter.version>` | `jeecg-boot/pom.xml:65` |
| 认证 | Apache Shiro **2.0.5** + JWT **4.5.0**，Redis 会话 | `<Boot>/CLAUDE.md` |
| 序列化 | FastJSON 2 | 同上 |

> **artifactId 是 `...spring-boot3-starter`**（Spring Boot 3 变体），不是常见的 `dynamic-datasource-spring-boot-starter`。写 pom 时抄错会拉不到包。

---

## 2 数据库：单库 `jeecg-study`

> **V1.1 已改为单库**（ADR-015 取代 ADR-001）。平台表与业务表同库，**不再用 `@DS("study")`**。

### 2.1 配置（已改好，在基座里）

```
<Boot>/jeecg-module-system/jeecg-system-start/src/main/resources/application-dev.yml
  行 151  datasource:
  行 152    master:
  行 153      url: jdbc:mysql://127.0.0.1:3306/jeecg-study?characterEncoding=UTF-8&...
  行 154      username: root
  行 155      password: ${DB_PASSWORD:root}   # 环境变量注入，真实口令不入库
  行 156      driver-class-name: com.mysql.cj.jdbc.Driver
```

`application-test.yml` / `application-prod.yml` / `application-docker.yml` 同步改为 `jeecg-study`；
**口令一律走环境变量**：dev / test 写 `${DB_PASSWORD:root}`，prod 写 `${DB_PASSWORD}`（无缺省）。
**真实口令不写进任何入库文件**——本机口令只存在于环境变量里。

> `context-path: /jeecg-boot` 是 **HTTP 前缀**，不是库名，**不要改**（改了前端代理要跟着改）。

### 2.2 平台表从哪来

| 文件 | 内容 |
|---|---|
| `<Boot>/jeecg-boot/db/jeecgboot-mysql-5.7.sql` | 上游原文件，**133 张表 + 7446 条种子数据**；第 1–2 行硬编码库名 `jeecg-boot` |
| `05-公共资源/2026103018-平台库脚本.py` | **只改库名**，生成 `jeecg-study` 版本 |
| `02-代码开发/02-数据库/03-脚本/01-平台库初始化.sql` | 生成产物（本地初始化用） |
| `<Boot>/jeecg-boot/db/jeecg-study-mysql.sql` | 同一产物（docker 初始化用，Dockerfile 已指向它） |

> **上游原文件保持不动**，便于日后对照 v3.9.2 升级。改库名只动前 2 行。

### 2.3 将来若要拆库（**当前不适用**）

当前口径是**单库**（ADR-015）：平台表（133 张）与业务表（17 张）同在 `jeecg-study`，**全仓不应出现任何 `@DS(` 用法**。

若将来确实要拆库：在 `spring.datasource.dynamic.datasource` 下加一个键，业务 Service 方法加 `@DS(...)`（**加在 Service 方法上，不是 Mapper**），并**先验证与 `@Transactional` 的交互**——dynamic-datasource 的数据源切换与 Spring 事务绑定有先后关系，位置错了事务会绑到旧数据源。
底座依赖已在 classpath 上（`jeecg-boot-base-core/pom.xml:128-133`）。

---

## 3 认证栈（多用户改造的核心）

### 3.1 底座登录全流程

**权威参照**：`<Boot>/jeecg-module-system/jeecg-system-biz/src/main/java/org/jeecg/modules/system/controller/LoginController.java`

| 步骤 | 代码 | 行 |
|---|---|---|
| 入口 | `@PostMapping("/sys/login")` | 83–85 |
| 密码解密 | `AesEncryptUtil.resolvePassword(sysLoginModel.getPassword())` | 89 |
| 失败锁定 | `isLoginFailOvertimes(username)`（5 次锁 10 分钟） | 93 |
| 验证码 | `validateCaptcha(sysLoginModel, result)` | 98 |
| 查用户 | `sysUserService.getOne(queryWrapper)` | 104–106 |
| 校验密码 | `PasswordUtil.encrypt(username, password, sysUser.getSalt())` | 113 |
| **签发 token** | `JwtUtil.sign(username, syspassword, clientType)` | 475 |
| 存 Redis | `redisUtil.set(CommonConstant.PREFIX_USER_TOKEN + token, token)` | 476 |
| 返回 | `obj.put("token", token)` | 482 |

**登出**：`POST /sys/logout`（行 182–203），会清 Redis 的 token / shiro 缓存 / 用户缓存。

### 3.2 本项目的两种登录方式

| 身份 | 凭据 | 端点 | 实现 |
|---|---|---|---|
| 家长 | 用户名 + 密码 | `POST /sys/login` | **底座原生，不新增** |
| 孩子 | **PIN**（4–6 位数字） | `POST /api/v1/study/kid/login` | 校验 PIN → **复用底座的 `JwtUtil.sign` + Redis 发 token** |

> **孩子也是一个 `sys_user`**（角色 `study_kid`）。这样 Shiro/JWT/Redis/`@RequiresPermissions`/菜单权限**全部复用底座**，不新增第二套认证栈。
> 孩子的 token 与家长**完全同构**，只是额外带 `childId` claim。

### 3.3 免认证路径

底座用 `@IgnoreAuth`（`org.jeecg.config.shiro.IgnoreAuth`）标记免认证接口，
路径还需登记到 `jeecg.shiro.excludeUrls` 配置。

本项目的免认证接口：`/api/v1/study/kid/lookup`、`/api/v1/study/kid/login`。

### 3.4 密码哈希

`org.jeecg.common.util.PasswordUtil.encrypt(username, password, salt)` —— **用户名为盐的组成部分**。
所以 PIN 与家庭码也用同款：`PasswordUtil.encrypt(childCode, pin, pinSalt)`。
**不存明文**；家庭码与 PIN 的明文**只在生成/重置时返回一次**。

---

## 4 后端 Controller 标准写法

**唯一权威参照**（jeecg-study 的 Controller 一律照它写）：

```
<Boot>/jeecg-boot-module/jeecg-module-monitor/src/main/java/org/jeecg/modules/monitor/alarm/controller/MonitorAlarmModelController.java
```

### 4.1 类级注解（行 24–27）

```java
@Tag(name = "monitorV2-预警模型")          // 行 24  OpenAPI 分组名
@RestController                            // 行 25
@RequestMapping("/api/v1/monitor/alarm/model")   // 行 26  完整路径，不带尾斜杠
public class MonitorAlarmModelController {  // 行 27
```

> jeecg-study 对应写法：`@RequestMapping("/api/v1/study/{资源}")`，**不要**再加一层 `/api`。

### 4.2 分页列表（行 34–47）

```java
@Operation(summary = "分页列表")                                    // 行 34
@GetMapping("/list")                                               // 行 35
@PermissionData(pageComponent = "mtv2/alarm/RuleConfig")           // 行 36  前端组件路径，用于数据权限
@RequiresPermissions("monitor:alarm:rule")                         // 行 37  Shiro 权限码
public Result<IPage<MonitorAlarmModel>> list(MonitorAlarmModel query,
        @RequestParam(name = "pageNo",  defaultValue = "1")  Integer pageNo,
        @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
        HttpServletRequest req) {
    QueryWrapper<MonitorAlarmModel> qw =
        QueryGenerator.initQueryWrapper(query, req.getParameterMap());   // 行 42
    IPage<MonitorAlarmModel> page = service.page(new Page<>(pageNo, pageSize), qw);  // 行 43
    // ... 补字段
    return Result.OK(page);                                                // 行 47
}
```

**四个必抄的要点：**

1. **分页参数用 `pageNo` / `pageSize`**（不是 `page` / `size`）。
2. **`QueryGenerator.initQueryWrapper(query, req.getParameterMap())`** —— 前端传的查询条件靠它转成 `QueryWrapper`，不要自己拼 `eq`。
3. **`service.page(new Page<>(pageNo, pageSize), qw)`** —— MyBatis-Plus 原生分页。
4. **`Result.OK(page)`** —— 返回 `Result`，不是裸对象。

### 4.3 增删改（行 66–95）

```java
@Operation(summary = "新增")                                        // 行 66
@PostMapping("/add")                                               // 行 67
public Result<String> add(@RequestBody MonitorAlarmModel entity) {
    service.save(entity);
    return Result.OK("添加成功");                                   // 行 71
}

@Operation(summary = "编辑")                                        // 行 74
@RequestMapping(value = "/edit", method = {RequestMethod.PUT, RequestMethod.POST})  // 行 75
public Result<String> edit(@RequestBody MonitorAlarmModel entity) {
    service.updateById(entity);
    return Result.OK("编辑成功");                                   // 行 81
}
```

> **本项目的接口规约与 JeecgBoot 原生不同**：本项目接口设计说明书规定用 REST 风格
> （`POST /error-pool`、`PUT /error-pool/{code}/cause`），**不照抄** JeecgBoot 的 `/add` `/edit` `/delete` 风格。
> Controller 骨架照抄，**路径风格按项目接口设计说明书**。这两件事不要混。

### 4.4 返回值

| 用法 | 出处 |
|---|---|
| `Result.OK(data)` | 上表行 47 / 103 / 116 |
| `Result.OK("新增成功")` | 行 71 / 81 / 95 |
| `Result.error(msg)` | `MonitorAlarmModelController.java` 分组统计分支 |

`Result` 类位置：`<Boot>/jeecg-boot-base-core/src/main/java/org/jeecg/common/api/vo/Result.java`

> `PageResult` 在 3.9.2 中**已不存在**（全仓搜不到该类），分页统一用 `Result<IPage<T>>`。别照旧文档写 `PageResult`。

---

## 5 前端写法

### 5.1 HTTP 调用

```ts
import { defHttp } from '/@/utils/http/axios';                 // src/views/mtv2/alarm/ModelList.vue:195
import { applyAlarmModelBatch } from '/@/api/mtv2/monitor';     // :196
await defHttp.post({ url: api + '/edit', data: payload });      // :472
await defHttp.post({ url: api + '/add',  data: payload });      // :475
```

- **统一用 `defHttp`**，不要用裸 `axios`。
- API 层单独放 `src/api/study/study.ts`，页面只引 API 层的函数，不在 `.vue` 里散写 URL。

### 5.2 分页

- 框架自带的通用列表页方案：`src/hooks/system/useListPage.ts`（JeecgBoot 标准 CRUD 页用这个）。
- monitorV2 业务页用的是自研方案：`src/views/mtv2/utils/pagination.ts` 的 `createMtv2Pagination`（`ModelList.vue:197`）配合 `Mtv2DataList` / `Mtv2ListToolbar` / `Mtv2ActionColumn` / `Mtv2ListSummary` 等组件（`src/views/mtv2/components/`）。
  > ⚠️ **monitorV2 不是本项目的基座**——本项目基座是**上游 JeecgBoot 3.9.2**。monitorV2 的自研分页方案**不要**照搬，只可参考业务语义。

> **jeecg-study 的选择**：管理端（12 页）用 `useListPage` + `BasicTable`（上游封装，**不是**手写 `a-table`），分页由 `BasicTable` 内置；样式按《产品设计规范》；
> **学习端（18 页）是独立全屏布局**，不套任何列表页方案——不要把管理端模式套到孩子端。

### 5.3 学习端与家长端布局

- 独立路由布局 `layouts/study`（学习端）/ `layouts/parent`（家长端），**不加载管理端顶栏与侧栏**。
- 配色 / 字号 / 间距 / 圆角一律取《产品设计规范》实测值，不引入新色值。

---

## 6 代码修改痕迹（底座强制约定）

> **`update-begin` / `update-end` 注释是底座的硬性约定**（`<Boot>/CLAUDE.md`），
> 既有代码里到处都是——**新增或修改代码块必须照做**，否则与上游风格脱节。

```java
//update-begin---author:作者 ---date:YYYY-MM-DD  for：【bug号/需求号】修改说明-----------
// 新增或修改的代码
//update-end---author:作者 ---date:YYYY-MM-DD  for：【bug号/需求号】修改说明-----------
```

| 场景 | 包裹范围 |
|---|---|
| 新增方法 | `update-begin` 在方法声明前，`update-end` 在方法结束的 `}` 后 |
| 修改已有方法 | **只包裹被修改的代码段**，不包裹整个方法 |
| `author` | 实际修改人（项目内统一写 `mavis`） |
| `date` | `YYYY-MM-DD` |
| `for` | bug 号或需求号 + 简要说明（**本项目写需求编号，如 `F-FAM-05`**） |

---

## 7 快速自检：写完后对照

- [ ] Controller 类级是 `@Tag` + `@RestController` + `@RequestMapping`，路径**不带**多余 `/api` 前缀
- [ ] 分页参数名是 `pageNo` / `pageSize`
- [ ] 用了 `QueryGenerator.initQueryWrapper(...)`，没手拼 `QueryWrapper`
- [ ] 返回 `Result.OK(...)` / `Result.error(...)`，没有裸对象
- [ ] 没有用 `PageResult`（3.9.2 已无此类）
- [ ] 包结构是**域优先**：`study/{域}/controller|entity|mapper|service|service/impl`
- [ ] 新增/修改的代码块有 `update-begin` / `update-end` 注释
- [ ] `judge` / `engine` 包**没有**任何 AI 客户端依赖（R-02 的编译期断言）
- [ ] 答案字段没出现在取题接口响应里（R-01）
- [ ] **孩子维度的过滤只写在 `family/scope/ChildScopeHelper`**，没有散落在各处
- [ ] 家长侧的 child 集合是**实时查关系表**并与前端参数求交集，没有直接信任前端 `childId`
- [ ] 前端用 `defHttp`，学习端/家长端用独立全屏布局
- [ ] 没有引入第三方统计 / 广告 / 崩溃上报 SDK
