# GameWiki — AI 智能游戏攻略知识库

基于 **Spring Boot 3 + Spring AI + Vue 3** 的前后端分离项目：面向游戏玩家提供游戏库检索、游戏与图文攻略浏览、收藏点赞与基于本地知识库的 AI 流式问答；面向管理员提供涵盖总览监控、游戏目录管理、攻略编辑与审核台、用户治理、媒体上传及批量导入导出的完整运营后台。

> 五人团队课程设计 ｜ GitHub 仓库：`https://github.com/Hiyori0520/GameWiki` ｜ 稳定分支：`main`

---

## 一、技术栈

| 层次 | 技术 | 说明 |
| --- | --- | --- |
| 后端 | Spring Boot 3.3.5 / Java 17 | REST API，端口 **8081** |
| AI | Spring AI 1.0.0 + DeepSeek | Function Calling 本地知识库检索、SSE 流式问答与历史会话 |
| 数据 | MySQL 8.0 / MyBatis-Plus 3.5.7 / Spring JDBC | 库名 `ai_game_kb`，含业务基础表与 15 张共享目录/后台扩展表 |
| 缓存 | Redis 5.0 | 登录 token、攻略缓存、AI 会话记忆 |
| 文件与表格 | EasyExcel 4.0.3 / Hutool 5.8.29 | 后台游戏数据 CSV/XLSX 批量导入与导出、图片字节校验入库 |
| 前端 | Vue 3.5 + Vite 5 + Vue Router 4 | 端口 **5173**，用户端与管理端统一深色游戏库主题 |
| UI 与可视化 | Element Plus / Ant Design Vue / ECharts 6 / marked + DOMPurify | 管理端图表、Markdown 安全预览与目录导航 |
| 接口文档 | Knife4j 4.5.0 (springdoc) | `http://localhost:8081/doc.html` |

---

## 二、主要功能模块

### 1. 用户端（7 个页面）

| 页面 | 路由 | 主要功能 |
| --- | --- | --- |
| 首页 | `/home` | 热门游戏、最新攻略、搜索入口、AI 助手入口与角色快捷导航 |
| 游戏库 | `/games` | 关键词搜索、分类/平台/标签筛选、排序、分页与深色游戏卡片展示 |
| 游戏详情 | `/games/:id` | 游戏基础与核验资料、高清封面、收藏状态与关联攻略 |
| 攻略详情 | `/guides/:id` | Markdown 图文攻略阅读、点赞、收藏与关注作者 |
| AI 攻略助手 | `/ai/chat` | 基于本地知识库的 SSE 流式问答、多会话历史记录管理 |
| 个人中心 | `/profile` | 个人资料编辑、修改密码、收藏列表、浏览历史与玩家攻略投稿 |
| 登录与注册 | `/auth` | 玩家账号登录、注册与身份分流 |

### 2. 管理端（10 个页面 + 统一权限布局）

> 路由前缀 `/admin`，统一由 `AdminLayout.vue` 提供侧边栏、面包屑、全局搜索、待审核通知与会话管理，路由守卫按 `/api/admin/v1/session` 下发的 17 项细粒度权限校验。详见 [`docs/管理端与后台接口说明.md`](docs/管理端与后台接口说明.md)。

| 序号 | 页面 / 模块 | 路由 | 对应文件 | 核心功能 |
| --- | --- | --- | --- | --- |
| 1 | 后台总览 | `/admin/dashboard` | `views/admin/Dashboard.vue` | 总量统计卡与 7 日变化、7/30/90 天趋势折线图、游戏分类环形图、最新内容、操作日志、系统健康状态 |
| 2 | 游戏管理 | `/admin/games`（含新增/编辑/详情/导入子路由） | `views/admin/GameManage.vue` | 多条件检索排序分页、游戏 CRUD、草稿/待审/发布/下架流转、批量上下架/删除/改分类/加标签、封面背景上传、CSV/XLSX 导入与导出任务 |
| 3 | 攻略管理与审核台 | `/admin/guides`、`/admin/guides/reviews`、`/admin/guides/categories` | `views/admin/GuideManage.vue` | 攻略列表检索分页、待审核队列（审核台批准/驳回与原因记录）、下架/复制/删除、分类与标签字典管理、JSON 批量导入导出 |
| 4 | 攻略编辑器 | `/admin/guides/new`、`/admin/guides/:id/edit` | `views/admin/GuideEditor.vue` | 关联游戏异步检索、Markdown 编辑与 XSS 过滤预览、自动目录跳转、封面/正文图片上传、剧透标记、草稿保存与修订送审 |
| 5 | 用户管理 | `/admin/users`、`/admin/users/:id`、`/admin/users/levels` | `views/admin/UserManage.vue` | 用户检索筛选分页、注册增长趋势、会员分布、活跃排行、昵称修改、封禁/解封与原因有效期审计、密码重置 |
| 6 | 用户详情面板 | `/admin/users/:id`（子视图） | `views/admin/users/UserDetail.vue` | 用户资料、会员身份、经验与贡献值、按权限脱敏邮箱展示及封禁历史记录 |
| 7 | 用户等级规则面板 | `/admin/users/levels`（子视图） | `views/admin/users/UserLevel.vue` | 平台 5 级用户成长规则与所需经验阈值展示 |
| 8 | 管理员信息 | `/admin/profile` | `views/admin/AdminProfile.vue` | 当前管理员身份、角色、已授权后台模块与 17 项权限明细展示及快捷入口 |
| 9 | 系统设置 | `/admin/settings` | `views/admin/SystemSettings.vue` | 管理员持久化偏好配置（侧边栏折叠状态、默认分页条数 20/50/100、总览趋势周期 7/30/90 天） |
| 10 | 无权限提示页 | `/admin/access` | `views/admin/AdminAccess.vue` | 401/403/服务异常场景下的友好拦截提示与返回引导 |

---

## 三、目录结构

```text
GameWiki/
├── backend/                          # Spring Boot 后端（业务 + 鉴权 + 共享目录 + 管理后台 + AI）
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/gamekb/
│       │   ├── auth/                 # 登录拦截器、@RequireRole 角色注解、UserContext
│       │   ├── common/               # Result、全局异常处理、分页封装
│       │   ├── config/               # Redis、MyBatis-Plus、CORS、Jackson、AI Tools
│       │   ├── controller/           # User / AiAgent / AiHistory / Catalog / CatalogAdmin / CatalogFiles 等
│       │   ├── dto/ entity/ mapper/  # 请求校验对象、实体与 MyBatis-Plus 映射
│       │   └── service/              # AiAgentService / CatalogDatabase / CatalogGame/Guide/UserService 等
│       └── resources/
│           ├── application.yml       # 公共配置（启动时自动执行 02/03 共享目录迁移脚本）
│           ├── application-local.yml.example
│           └── static/catalog/game-covers/  # 14 款已核验游戏官方原生高清 WebP 封面
├── frontend/                         # Vue 3 前端
│   └── src/
│       ├── admin/                    # 后台模块导航定义、持久化偏好、演示降级数据
│       ├── api/admin/                # 后台 HTTP 客户端、会话权限及 4 大业务 API 封装
│       ├── components/admin/         # 7 个后台通用组件（ECharts、弹窗、分页、统计卡、Markdown 预览等）
│       ├── layouts/                  # AdminLayout 后台统一布局与深色样式体系
│       ├── router/                   # 全站路由与后台细粒度权限守卫
│       ├── types/admin/              # 攻略与用户管理 TypeScript 类型定义
│       └── views/
│           ├── user/                 # 用户端 7 个页面
│           └── admin/                # 管理端 10 个页面（含 users/ 子视图）
├── database/                         # MySQL 建库、增量迁移、共享目录与已核验资料脚本
│   ├── 00_init.sql                   # 基础库与核心表初始化
│   ├── 01_like_history.sql           # 点赞与浏览历史表
│   ├── 02_shared_catalog.sql         # 共享目录、用户档案、后台偏好/日志/媒体/传输任务 15 张扩展表
│   ├── 03_verified_game_catalog.sql  # 14 款已核验游戏资料与高清封面路径
│   └── game-catalog-sources.md       # 游戏发行信息与官方封面来源核验记录
└── docs/                             # API 契约、后端开发规范、任务清单、用户端与管理端详细说明
```

---

## 四、团队分工与职责说明

| 成员 | 模块角色 | 主要职责 | 核心交付 |
| --- | --- | --- | --- |
| A（组长） | 后端基础与集成 | 后端基础架构、数据库基线、BCrypt 鉴权与角色拦截、项目集成 | 鉴权拦截器、通用配置、`00_init.sql`/`01_like_history.sql`、联调与重置密码扩展 |
| B | 基础业务后端 | 早期游戏/攻略与基础数据字典接口 | 旧版攻略与基础数据 CRUD、Redis 缓存 |
| C | 用户端页面 | 玩家端核心浏览与交互页面 | 游戏详情、攻略详情、个人中心等玩家页面及说明文档 |
| **D** | **管理端与后台接口** | **管理端十个页面、`/api/admin/v1` 后台接口、审核台与用户管理、MySQL 共享目录数据层** | **10 个后台页面、42 个后台管理接口、7 个后台通用组件、15 张目录/运营扩展表与数据服务** |
| E | AI 问答模块 | Spring AI 接入、知识库检索问答与流式输出 | `AiToolsConfig`、`AiAgentService`、SSE 流式接口与 AI 会话历史 |

### 核心模块详述：管理端与后台接口

1. **管理端前端体系（10 个页面 + 布局/权限/组件）**
   - 独立完成 `frontend/src/views/admin/` 下全部 **10 个管理端页面**（后台总览、游戏管理、攻略管理与审核台、攻略编辑器、用户管理、用户详情面板、用户等级规则面板、管理员信息、系统设置、无权限提示页）。
   - 设计并实现深色主题后台统一外壳（`AdminLayout.vue`、`admin.css`），集成侧栏折叠、动态面包屑、全局搜索、待审核通知提醒与 17 项细粒度前端路由/操作权限守卫。
   - 抽象封装 **7 个后台复用组件**（`AdminChart`、`AdminDialog`、`AdminGameIcon`、`AdminPager`、`AdminStats`、`GuideGameSelect`、`GuideMarkdown`）及 **5 个强类型 API 客户端模块**（`api/admin/client.ts`、`dashboard.ts`、`game.ts`、`guide.ts`、`user.ts`），并支持开发态本地演示模式降级。
2. **后台业务接口与文件传输（`/api/admin/v1/*`）**
   - 设计并实现 `CatalogAdminController` 下 **42 个后台管理接口**（含后台会话权限、总览统计/趋势/分布/日志/健康监控、游戏 CRUD 与状态流转及批量操作、攻略 CRUD/审核台批准驳回/下架/分类标签/JSON 导入导出、用户列表/详情/统计/趋势/分布/活跃排行/等级规则/资料修改/封禁解封审计/密码重置、管理员偏好读写）。
   - 实现 `CatalogFilesController` **6 个文件与传输接口**（JPG/PNG/WebP 文件头魔数校验入库与 `/catalog/media/{id}` 静态缓存读取、游戏 CSV/XLSX 异步格式导入与导出任务状态查询及报告下载），使后台 `/api/admin/v1` 体系形成完整闭环。
3. **MySQL 共享目录统一与数据服务层**
   - 设计 `database/02_shared_catalog.sql` 共 **15 张扩展表**（覆盖游戏/攻略扩展信息、分类平台字典、用户档案与等级规则、管理员偏好、操作审计日志 `admin_activity`、封禁日志 `user_ban_log`、媒体表 `catalog_media` 与导入导出任务表 `catalog_transfer` 等）。
   - 实现 `CatalogDatabase`、`CatalogGameService`、`CatalogGuideService`、`CatalogUserService` 及玩家端共享目录控制器 `CatalogController`（15 个 `/api/catalog/*` 接口），将管理端维护的数据与玩家端浏览/投稿/互动统一到同一套 MySQL 数据源。
4. **关联模块与整站优化**
   - **玩家游戏库 UI 重构与主题统一**：重设计玩家端游戏库页面（`GameList.vue`、`game-library.css`），统一全站深色视觉风格并优化首页与后台导航互通。
   - **已核验游戏资料与高清封面整理**：整理 14 款游戏的官方发行信息与 14 张原生高清 WebP 封面（`database/03_verified_game_catalog.sql`、`database/game-catalog-sources.md`、`static/catalog/game-covers/`）。
   - **项目文档整理**：维护项目核心文件的用途与实现说明，完善 [`docs/管理端与后台接口说明.md`](docs/管理端与后台接口说明.md) 与 [`docs/API契约.md`](docs/API契约.md) 中的后台接口定义。

> 完整页面拆解、权限清单、接口明细与扩展表结构请查阅 [`docs/管理端与后台接口说明.md`](docs/管理端与后台接口说明.md)。

---

## 五、本地启动步骤

### 0. 环境要求

- JDK 17+、Maven 3.8+、Node.js 18+
- MySQL 8.0 已启动（本机端口以实际为准，常见为 3306 / 3307）
- Redis 5.0+ 已启动（默认 6379）

### 1. 获取代码并初始化基础数据库

```powershell
git clone https://github.com/Hiyori0520/GameWiki.git
cd GameWiki

mysql -h127.0.0.1 -P3306 -uroot -p < .\database\00_init.sql
mysql -h127.0.0.1 -P3306 -uroot -p ai_game_kb < .\database\01_like_history.sql
```

> `02_shared_catalog.sql` 与 `03_verified_game_catalog.sql` 已配置在后端 `application.yml` 的 `spring.sql.init.schema-locations` 中，后端启动时会自动幂等执行，也可手动导入。

### 2. 配置本地私密参数（必做）

```powershell
Copy-Item .\backend\src\main\resources\application-local.yml.example .\backend\src\main\resources\application-local.yml
```

打开 `backend/src/main/resources/application-local.yml`，填入本机 MySQL 连接端口、密码及 DeepSeek API Key。**该文件已被 `.gitignore` 排除，切勿提交到仓库。**

### 3. 启动后端

```powershell
cd .\backend
mvn spring-boot:run
```

看到 `AI智能游戏攻略知识库系统 启动成功！` 即启动完成。在线接口文档：`http://localhost:8081/doc.html`。

### 4. 启动前端

```powershell
cd .\frontend
npm install
npm run dev
```

浏览器访问 `http://localhost:5173`（若端口占用以 Vite 终端实际输出地址为准）。

---

## 六、演示账号

| 账号 | 密码 | 角色 | 可访问范围 |
| --- | --- | --- | --- |
| `admin` | `gamewiki2026` | `ADMIN` | 登录后默认进入 `/admin` 管理端，可访问全部 10 个后台页面与用户端页面 |
| `player1` | `gamewiki2026` | `PLAYER` | 登录后默认进入 `/home` 用户端，支持浏览、收藏、点赞、投稿与 AI 问答 |

密码以 BCrypt 密文存储；登录 token 有效期 12 小时，退出即在 Redis 失效。

---

## 七、接口与协作约定

详见 [`docs/API契约.md`](docs/API契约.md) 与 [`docs/后端开发规范.md`](docs/后端开发规范.md)：

1. 统一返回结构 `{ code, message, data }`（字段名为 `message`，非 `msg`）。
2. 请求头统一携带 `Authorization: <token>`，未登录返回 `401`，无管理员权限返回 `403`。
3. 业务分页基础接口使用 `page` / `size`，管理端 `/api/admin/v1/*` 接口使用 `page` / `pageSize` 并返回 `{ records, total, page, pageSize }`。
4. 提交代码前请确认 `target/`、`node_modules/`、`application-local.yml` 等未进入暂存区。

---

## 八、常见问题

| 现象 | 原因与处理 |
| --- | --- |
| 启动报 `Failed to configure a DataSource` | 未创建 `application-local.yml`，按步骤 2 复制模板并填写配置 |
| 登录提示“缓存服务未连接” | 本机 Redis 未启动，启动 Redis 后重试 |
| 连不上数据库 | 检查 `application-local.yml` 中 MySQL 端口、库名与密码是否与本机一致 |
| 前端调接口返回 401 / 跳回登录页 | 登录态失效或未登录，重新登录获取 `localStorage` 中的 `kb_token` |
| 管理员后台提示 403 / 跳到 `/admin/access` | 当前登录账号角色不是 `ADMIN`，请使用管理员账号登录 |
| 访问 `/swagger-ui.html` 白页 | 改用 `/doc.html` 或 `/swagger-ui/index.html` |

---

## 九、当前完成状态

| 模块 | 状态 | 说明 |
| --- | --- | --- |
| 数据库基础表、共享目录扩展表与已核验资料 | ✅ 已完成 | `00_init.sql` ~ `03_verified_game_catalog.sql` + 14 张高清封面 |
| 登录鉴权（BCrypt + Redis Token + 角色拦截） | ✅ 已完成 | 支持登录、注册、退出、改密与管理员重置密码 |
| 管理端前端（10 个页面 + 权限布局 + 7 个通用组件） | ✅ 已完成 | 全部对接 `/api/admin/v1/*` 真实接口 |
| 后台接口与文件导入导出（`/api/admin/v1/*`） | ✅ 已完成 | 游戏/攻略/审核台/用户治理/总览监控/图片上传与 CSV/XLSX 传输 |
| 玩家端页面与共享目录交互（`/api/catalog/*`） | ✅ 已完成 | 游戏库重设计、详情浏览、收藏点赞关注、历史记录与攻略投稿 |
| AI 问答与流式输出（`/api/ai/*`） | ✅ 已完成 | 本地知识库检索、SSE 流式回复与多会话历史管理 |
