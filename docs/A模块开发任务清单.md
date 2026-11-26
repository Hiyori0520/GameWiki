# A 模块开发任务清单

> 角色：A（组长）｜ 范围：后端基础、数据库、登录权限、团队集成 ｜ 目录：`backend/`、`database/`

---

## 0. 先决问题：必须先拍板，否则后面全是白工

当前仓库存在两套互不相通的后端：

| 分支 | 包名 | 状态 |
| --- | --- | --- |
| `main` | `com.gamewiki` | 空壳，只有一个 `HealthController` |
| `feat/ai-kb-full` | `com.gamekb` | 完整实现，含 AI、数据库、缓存 |

**A 的第一件事不是写代码，是决定主线。** 建议：以 `feat/ai-kb-full` 为基线（它有真实可跑的业务），把 `com.gamewiki` 空壳废弃，然后重写 README 对齐现实。两套并存的话，B/C/D/E 的代码会合不进来。

---

## 1. 数据库（优先级最高，B 和 E 都卡在这里）✅ 已完成

产出目录：`database/` ｜ 提交：`8b4eca9`

- [x] `00_init.sql`：建库 + 6 张表 + 种子数据，**一条命令**初始化完成，可重复执行
- [x] 合并冗余表：`game_category` 字段与 `game_info` 完全重复且代码从未使用，已删除；`game_code`、`cover_url`、`sort_order` 并入 `game_info`
- [x] 新增 `game_favorite` 表：`UNIQUE KEY uk_user_guide(user_id, guide_id)`，B 的收藏接口可直接开工
- [x] 密码改为 BCrypt 密文（60 字符），演示账号统一 `gamewiki2026`
- [x] 补充索引：`game_guide(game_id, audit_status)`、`(audit_status, create_time)`、`ai_chat_log(user_id, create_time)`
- [x] 写 `database/README.md`：导入命令、演示账号、表结构一览、**修改数据库的流程**（新表加增量脚本，禁止手工改库）
- [x] 验收：已在 MySQL 8.0.39 上实际执行通过（6 表、2 用户、2 游戏、3 攻略、1 收藏，哈希长度 60）

**执行中发现的额外问题：**

- ⚠️ `application.yml` 写的是 `localhost:3307`，但**本机 MySQL 实际监听 3306**，后端现在连不上库。需要 A 统一端口后再联调。
- ⚠️ `legacy/ai_game_kb_navicat_export.sql` 已归档，仅作参考，不要用它初始化。

---

## 2. 后端基础 ✅ 已完成

产出目录：`backend/src/main/java/com/gamekb/`

- [x] `common/Result.java`：业务失败不再一律 `500`，改为 `200/400/401/403/404/500` 语义
- [x] 新增 `common/ErrorCode` 枚举 + `common/BizException`（Service 直接 throw，不再手写 if-return）
- [x] 新增 `common/GlobalExceptionHandler`：业务异常、参数校验、类型不匹配、JSON 格式错误、方法不支持、路径不存在、Redis 掉线、兜底共 10 类
- [x] 分页：`config/MybatisPlusConfig` 注册 `PaginationInnerInterceptor`（单页上限 100）+ `common/PageQuery` + `common/PageResult<T>`
- [x] 参数校验：引入 `spring-boot-starter-validation`，新建 `dto` 包（Login/Register/ChangePassword/Chat），Controller 加 `@Valid`
- [x] 配置外置：`application.yml` 去敏，密钥移入 `application-local.yml`（已 gitignore）+ `.example` 模板
- [x] CORS 收敛为 `localhost:5173/5174` 白名单
- [x] ★ 新增 `backend/src/test/java/com/gamekb/FoundationTest.java`，4 个冒烟测试（分页/映射/边界/异常码）
- [x] 配套 `docs/后端开发规范.md`，B/E 照抄即可

**实测结果：**

| 场景 | 结果 |
| --- | --- |
| `mvn test` | 4 个测试全通过；SQL 日志可见 `LIMIT ?,?` 与独立 `COUNT(*)`，分页真实生效 |
| 空账号登录 | `400 账号不能为空`（由 DTO 注解产出） |
| 非法 JSON 请求体 | `400 请求体格式错误，请检查 JSON 是否合法` |
| `/api/guide/abc` | `400 参数格式不正确：id` |
| 访问不存在接口 | `404 接口不存在：api/not-exist` |
| GET 调 POST 接口 | `400 不支持的请求方法：GET` |
| AI 提交空提问 | `400 提问内容不能为空`（原来是 500） |
| 停掉 Redis 后登录 | `500 缓存服务未连接，请先启动 Redis（默认端口 6379）后重试` |

> 遗留：统一请求日志（logback 打印耗时）没做，优先级低于功能，等联调再说。

---

## 3. 登录权限（工作量最大，也是当前最大的漏洞）

### 现状（严重）

- 登录是**明文比对**：`wrapper.eq(SysUser::getPassword, password)`
- 签发 token 后存进 Redis，**但没有任何拦截器校验它**
- 结果是：`/api/guide/delete`、`/api/basic/games/{id}`、`/api/user/*` 全部裸奔，任何人 curl 就能删数据
- 前端 `request.js` 老老实实带上了 `Authorization` 头，后端从头到尾没读过
- 没有登出接口，前端只删 localStorage，Redis 里 token 还能用 12 小时

### 已完成 ✅

- [x] 引入 `spring-security-crypto`（只要 BCrypt，不引入整套 Security 过滤链）：注册 `encode()`，登录 `matches()`
- [x] 签发 token：UUID 存 Redis，12 小时过期，每次请求自动续期
- [x] 新增 `auth/AuthInterceptor`：读 `Authorization` 头 → 查 Redis → 校验 `status` → 写入 `ThreadLocal<UserContext>` → `afterCompletion` 清理
- [x] 新增 `config/WebMvcConfig`：拦截 `/api/**`，放行 `/api/user/login`、`/api/user/register`
- [x] 新增 `@RequireRole` 注解，已标注到 `guide/all`、`guide/update`、`guide/{id} DELETE`、`basic/**` 写操作
- [x] 新增 `/api/user/logout`、`/api/user/info`、`/api/user/password`
- [x] `request.js` 已处理 401 跳登录、403 提示无权限；`MainLayout` 登出改为调用后端
- [x] 顺手修掉的两个契约问题：`Result` 字段改为 `message`；`LocalDateTime` 统一序列化为 `yyyy-MM-dd HH:mm:ss`

**实测结果（本机 MySQL 8.0.39 + Redis 5.0，服务跑在 8099）：**

| 场景 | 结果 |
| --- | --- |
| 未带 token 访问 `/api/guide/all` | `401 未登录，请先登录` |
| admin / gamewiki2026 登录 | `200`，返回 token + userInfo |
| admin 错误密码 | `401 账号或密码错误` |
| PLAYER 访问 `/api/guide/all` | `403 无访问权限` |
| PLAYER 访问 `/api/guide/search` | `200` 正常返回 |
| PLAYER 删除攻略 | `403 无访问权限` |
| 登出后复用原 token | `401 登录已过期` |
| 注册弱密码 `123` | `400 密码至少 6 位` |

---

## 4. 团队集成（组长的隐性工作，别拖到最后）

- [x] 目录对齐方案：`ai-game-kb` → `backend/`、`ai-game-kb-web` → `frontend/`，同时删除 `com.gamewiki` 旧骨架
- [ ] 重写 README：包名是 `com.gamekb`（不是 `com.gamewiki`）、UI 库是 Element Plus（不是 Ant Design Vue）、Spring Boot 3.3.5（不是 3.5.10）、12 个页面里实际只做了 4 个
- [ ] 分支落地：`dev` 作为集成分支，五人各自 `feature/xxx`，禁止直接推 `dev`
- [ ] 密钥治理（**今天就做**）：`application.yml` 里的 DeepSeek `api-key` 和数据库密码已推到远程仓库，去 DeepSeek 控制台吊销重发，历史提交里的密钥要么改密要么认栽但必须换新
- [ ] 接口契约贴到 Knife4j：统一 `Result<T>` 结构、分页字段名、鉴权头格式，让 C/D 不用问人
- [ ] 约定端口表：前端 5173、后端 8081、MySQL 3306、Redis 6379，写进 README
- [ ] 集成检查清单：每人合并前自查（能启动 / 无明文密钥 / 接口有文档 / 不提交 node_modules、target）

---

## 5. 建议排期

| 天 | 内容 | 交付 |
| --- | --- | --- |
| D1 | 拍板主线 + 数据库脚本重写 | `database/00_init.sql`、`database/README.md` |
| D2 | 统一返回、全局异常、分页、参数校验 | 后端能正常启动、B 可直接用 |
| D3–D4 | BCrypt + 拦截器 + 角色注解 + 登出 | 401/403 验收通过 |
| D5 | 配置外置、CORS、日志、README、接口文档 | 团队可独立联调 |
| D6–D7 | 全员联调、修 bug、演示数据准备 | 可演示版本 |

---

## 6. 组长的三个提醒

1. **先给契约，再要求别人写代码。** B 最怕的是接口返回格式今天一个样明天一个样。
2. **别把认证留到最后。** 现在是"能跑但裸奔"，等 C/D 页面全接完了再改鉴权，前端要重测一遍。
3. **密钥已经泄露了。** 这不是代码问题，是账号问题，优先级高于任何功能。
