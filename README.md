# GameWiki — AI 智能游戏攻略知识库

基于 **Spring Boot 3 + Spring AI + Vue 3** 的前后端分离项目：玩家浏览游戏与攻略、向 AI 提问本地知识库；
管理员维护游戏资料、审核攻略、查看统计。

> 五人团队课程设计 ｜ 集成分支：`dev` ｜ 稳定分支：`main`

---

## 一、技术栈

| 层次 | 技术 | 说明 |
| --- | --- | --- |
| 后端 | Spring Boot 3.3.5 / Java 17 | REST API，端口 **8081** |
| AI | Spring AI 1.0.0 + DeepSeek | Function Calling 自主检索本地知识库 |
| 数据 | MySQL 8.0 / MyBatis-Plus 3.5.7 | 库名 `ai_game_kb`，端口见本地配置 |
| 缓存 | Redis 5.0 | token、攻略缓存、AI 会话记忆、热度榜 |
| 前端 | Vue 3 + Vite 5 + Element Plus | 端口 **5173** |
| 文档 | Knife4j 4.5.0 (springdoc) | `http://localhost:8081/doc.html` |

---

## 二、目录结构

```text
GameWiki/
├── backend/      # Spring Boot 后端（业务 + 鉴权 + AI）
│   ├── pom.xml
│   └── src/main/java/com/gamekb/
│       ├── auth/        # 登录拦截器、角色注解、用户上下文
│       ├── common/      # Result、异常处理、分页封装
│       ├── config/      # Redis、MyBatis-Plus、CORS、Jackson
│       ├── controller/  # 用户 / 攻略 / 基础数据 / 统计 / AI
│       ├── dto/         # 请求参数对象（含校验注解）
│       ├── entity/ mapper/ service/
│       └── resources/application.yml
├── frontend/      # Vue 3 前端
│   └── src/views/  # Login、Dashboard、GuideManage、BasicData、AiAdvisor
├── database/      # 建库脚本与说明
└── docs/          # API 契约、开发规范、任务清单
```

---

## 三、启动步骤

### 0. 前置

- JDK 17、Maven 3.8+、Node.js 18+
- MySQL 8.0 已启动（本机端口以实际为准，**3306 / 3307 都有人用**）
- Redis 5.0 已启动（默认 6379）

### 1. 初始化数据库

```powershell
mysql -h127.0.0.1 -P3306 -uroot -p < D:\Work\GameWiki\database\00_init.sql
```

脚本会建库 `ai_game_kb`、建 6 张表、灌入演示数据，可重复执行。详见 `database/README.md`。

### 2. 配置本地私密参数（必做，否则启动不了）

```powershell
cd D:\Work\GameWiki\backend\src\main\resources
Copy-Item application-local.yml.example application-local.yml
```

打开 `application-local.yml`，填自己的数据库密码与 DeepSeek API Key。
**该文件已被 `.gitignore` 排除，不要提交。**

### 3. 启动后端

```powershell
cd D:\Work\GameWiki\backend
mvn spring-boot:run
```

看到 `AI智能游戏攻略知识库系统 启动成功！` 即成功。接口文档：`http://localhost:8081/doc.html`

### 4. 启动前端

```powershell
cd D:\Work\GameWiki\frontend
npm install
npm run dev
```

访问 `http://localhost:5173`。

---

## 四、演示账号

| 账号 | 密码 | 角色 |
| --- | --- | --- |
| `admin` | `gamewiki2026` | ADMIN，可进后台、审核攻略 |
| `player1` | `gamewiki2026` | PLAYER，只能浏览、投稿、问 AI |

密码以 BCrypt 密文存储；token 有效期 12 小时，登出即失效。

---

## 五、分支规范

| 分支 | 用途 |
| --- | --- |
| `main` | 稳定版，仅用于验收与演示，不直接提交 |
| `dev` | 集成分支，各功能合并到这里 |
| `feature/backend-base` | A：后端基础、数据库、鉴权 |
| `feature/backend-business` | B：游戏/攻略业务接口 |
| `feature/user-pages` | C：用户端页面 |
| `feature/admin-pages` | D：管理端页面 |
| `feature/ai-service` | E：AI 问答与摘要 |

流程：从最新 `dev` 切功能分支 → 开发自测 → 推送 → 提 PR → 组长审核 → 合并 `dev`。

```powershell
git switch dev
git pull origin dev
git switch -c feature/your-task
# 改代码
git add <具体文件>
git commit -m "feat(模块): 说明"
git push -u origin feature/your-task
```

**不要 `git add .`**，`target/`、`node_modules/`、`application-local.yml` 都不能进仓库。

---

## 六、接口约定

详见 `docs/API契约.md`，三条最关键：

1. 返回结构固定 `{ code, message, data }`，**是 `message` 不是 `msg`**
2. 分页统一 `page` / `size`，返回 `data.{records,total,page,size}`
3. 鉴权头 `Authorization: <token>`，未登录 401、无权限 403

写业务代码前请先看 `docs/后端开发规范.md`，里面有异常、校验、分页的可复制示例。

---

## 七、常见问题

| 现象 | 原因与处理 |
| --- | --- |
| 启动报 `Failed to configure a DataSource` | 没建 `application-local.yml`，见步骤 2 |
| 登录提示"缓存服务未连接" | Redis 没启动，启动后重试 |
| 连不上数据库 | 端口不对。查 `application-local.yml` 里 JDBC 串的端口与本机是否一致 |
| 前端调接口 401 | 先登录，token 存 `localStorage` 的 `kb_token` |
| 端口被占用 | 后端 8081 / 前端 5173，被占时换端口并同步改前端 `request.js` 的 baseURL |
| 访问 `/swagger-ui.html` 白页 | 用 `/doc.html`，或 `/swagger-ui/index.html` |

---

## 八、当前进度

| 模块 | 状态 |
| --- | --- |
| 数据库脚本、建表与种子数据 | ✅ |
| 登录鉴权（BCrypt + 拦截器 + 角色） | ✅ |
| 后端基础设施（异常、分页、校验） | ✅ |
| AI 问答（Function Calling 检索本地库） | ✅ 基础版 |
| 攻略 / 游戏的业务 CRUD 接口 | 🚧 开发中（B） |
| 管理端页面 | 🚧 4 个页面可跑（D） |
| 用户端页面 | 🚧 页面已完成，待接接口与路由（C） |
| 收藏、浏览记录 | ⏳ 待开发 |


# GameWiki

**AI 智能游戏攻略知识库系统**

基于 **Vue 3 + TypeScript + Spring Boot + Spring AI** 规划开发的前后端分离 Web 应用，面向游戏玩家提供游戏资料浏览、攻略查询、图文攻略阅读及基于知识库的 AI 问答功能，并提供管理员后台维护游戏和攻略内容。

> **项目类型：** 五人团队 Java + AI 小型课程设计  
> **当前阶段：** 基础工程与前端页面搭建。部分页面使用演示数据；MySQL、MyBatis-Plus 和 Spring AI 业务接入尚未完成。以下功能为开发目标，不代表全部已经上线。

## 一、主要功能

### 用户端（7 个页面）

| 页面 | 路由 | 主要功能 |
| --- | --- | --- |
| 首页 | `/` | 轮播推荐、热门游戏、最新攻略、搜索入口、AI 助手入口 |
| 游戏库 | `/games` | 游戏列表、关键词搜索、类型及标签筛选、排序、分页 |
| 游戏详情 | `/games/:id` | 游戏基本资料、简介、标签、关联攻略 |
| 攻略详情 | `/guides/:id` | 图文攻略阅读、章节目录、收藏入口 |
| AI 攻略助手 | `/ai/chat` | 选择游戏、知识库问答、查看引用来源和对话记录 |
| 个人中心 | `/profile` | 个人资料、收藏列表、浏览记录 |
| 登录与注册 | `/auth` | 用户身份验证及账号注册 |

### 管理端（5 个页面）

| 页面 | 路由 | 主要功能 |
| --- | --- | --- |
| 后台总览 | `/admin` | 游戏、攻略、用户统计及可视化图表 |
| 游戏管理 | `/admin/games` | 游戏资料增删改查、封面、分类和标签维护 |
| 攻略管理 | `/admin/guides` | 攻略列表、检索、发布状态和内容管理 |
| 攻略编辑器 | `/admin/guides/new`、`/admin/guides/:id/edit` | 富文本编辑、图片、草稿、发布及 AI 摘要 |
| 用户管理 | `/admin/users` | 用户资料查询、角色及状态管理 |

### AI 功能（待开发）

1. **游戏攻略智能问答：** 按当前游戏检索已发布攻略，将相关内容交给大模型整理回答，并返回真实攻略来源。
2. **AI 攻略摘要：** 根据文章内容生成摘要和关键词，由管理员确认后保存。

第一版采用 MySQL 关键词检索结合大模型 API 的简化方案；向量检索、复杂推荐算法和完整社区系统不在小课设第一阶段范围内。

## 二、技术栈

| 层次 | 技术 | 说明 |
| --- | --- | --- |
| 前端 | Vue 3、TypeScript、Vite | 单页应用开发 |
| 前端组件 | Ant Design Vue、Ant Design Icons | UI 组件和图标 |
| 状态与路由 | Pinia、Vue Router 4 | 状态管理、页面路由 |
| 网络请求 | Axios | HTTP API 调用 |
| 可视化 | ECharts | 管理后台图表 |
| 后端 | Java、Spring Boot 3.5.10 | REST API 服务；项目编译目标为 Java 17 |
| 构建 | Maven | Java 依赖和构建管理 |
| 数据层（计划接入） | MySQL 8、MyBatis-Plus | 业务数据管理 |
| AI（计划接入） | Spring AI、大语言模型 API | 检索增强问答与文本摘要 |
| 协作 | Git、Gitee | 版本管理和团队合作 |

> 本机目前使用 JDK 21 和 Maven 3.10.0；团队成员只需安装与 `pom.xml` 配置兼容的 JDK 与 Maven 环境。配置以仓库中的 `pom.xml`、`package.json` 为准。

## 三、项目目录

```text
GameWiki/
├── frontend/                      # Vue 3 前端
│   ├── public/                    # 静态资源
│   ├── src/
│   │   ├── api/                  # HTTP API 封装
│   │   ├── components/           # 通用、游戏、攻略、AI 组件
│   │   ├── data/                 # 页面演示数据（联调后替换为接口）
│   │   ├── layouts/              # 用户端与管理端布局
│   │   ├── router/               # Vue Router
│   │   ├── stores/               # Pinia 状态
│   │   ├── styles/               # 公共样式
│   │   └── views/
│   │       ├── user/             # 用户端 7 个页面
│   │       └── admin/            # 管理端 5 个页面
│   └── package.json
├── backend/                       # Spring Boot 后端
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/gamewiki/
│       │   ├── BackendApplication.java
│       │   ├── controller/       # API 控制器
│       │   ├── service/          # 业务逻辑（逐步开发）
│       │   ├── mapper/           # 数据访问（逐步开发）
│       │   └── entity/           # 业务实体（逐步开发）
│       └── resources/
│           └── application.properties
├── database/                      # 数据库脚本及说明
├── docs/                          # 项目文档
├── .gitignore
└── README.md
```

> 目录树包含已建立的基础目录和规划中的模块；部分模块暂时为空，Git 不会追踪空文件夹。

## 四、开发环境与启动

### 1. 获取代码

在 **PowerShell** 中运行：

```powershell
git clone -b dev https://gitee.com/hiyori0520/game-guide-ai.git GameWiki
cd GameWiki
```

若仓库在 Gitee 上改名或修改路径，请以最新的克隆地址为准。

### 2. 启动前端

建议使用兼容当前 Vite 版本的 Node.js，并安装 npm。打开 PowerShell：

```powershell
cd .\frontend
npm ci
npm run dev
```

默认访问 `http://localhost:5173/`。如果该端口被占用，Vite 可能自动切换至 `5174` 等端口，**以终端显示的 `Local` 地址为准**。

### 3. 启动后端

确保已安装 JDK 和 Maven。在**另一个 PowerShell 窗口**运行：

```powershell
cd D:\Work\GameWiki\backend
mvn spring-boot:run
```

如果本机尚未将 Maven 加入 PATH，可使用已安装 Maven 的完整路径（仅示例，其他成员需改成自己的安装位置）：

```powershell
& "D:\Text\apache-maven-3.10.0\bin\mvn.cmd" spring-boot:run
```

当前配置的后端端口为 **8081**。基础健康检查地址：

```text
http://localhost:8081/api/health
```

预期响应：

```text
GameWiki Backend OK
```

> 端口与健康检查以实际运行结果为准；当前 README 记录的是现有配置和预期效果，不代表已经完成端到端验证。

### 4. 数据库与 AI 配置（后续）

MySQL、MyBatis-Plus 与 Spring AI 接入后，将在 `database/` 和 `docs/` 中补充建表 SQL、环境变量示例与配置步骤。**不要将数据库密码、API Key、个人访问令牌写入 Git 仓库。**

## 五、五人团队分工

以下为**岗位分工方案**，成员姓名可由团队内部填写。

| 成员 | 主要职责 | 核心交付 |
| --- | --- | --- |
| A（组长） | 后端基础、数据库、认证权限、项目集成 | 后端基础服务、通用配置、数据库脚本 |
| B | 游戏及攻略业务后端 | 游戏 CRUD、攻略 CRUD、筛选搜索和收藏接口 |
| C | 用户端前端 | 首页、游戏库、游戏详情、攻略阅读、个人中心等页面 |
| D | 管理端前端 | 后台总览、游戏管理、攻略管理、编辑器及用户管理页面 |
| E | AI 功能开发 | Spring AI 接入、攻略检索问答、AI 摘要与效果测试 |

所有成员共同参与接口联调、测试、项目报告和答辩准备。具体任务可根据进度调整。

## 六、Git 协作规范

- `main`：稳定版本，用于验收与演示；原则上不直接提交开发代码。
- `dev`：团队集成分支。
- `feature/backend-base`：后端基础。
- `feature/backend-business`：业务后端。
- `feature/frontend-user`：用户端前端。
- `feature/frontend-admin`：管理端前端。
- `feature/ai-service`：AI 业务开发。

建议从最新 `dev` 创建功能分支，完成开发和自测后发起合并请求，由团队进行代码检查，再合并到 `dev`。

```powershell
# 位于项目根目录
git switch dev
git pull origin dev
git switch -c feature/your-feature

# 修改代码并检查后提交
git status
git add .
git commit -m "feat: describe your change"
git push -u origin feature/your-feature
```

提交前务必检查 `git status`，不要上传 `node_modules/`、`target/`、IDE 缓存、密码或密钥。

## 七、当前进度

| 事项 | 状态 |
| --- | --- |
| Git 仓库与 `main`、`dev` 分支 | 已建立 |
| Vue 3 + Vite 基础工程 | 已创建并启动过 |
| 前端 12 个页面路由/占位结构 | 已建立；功能仍在开发 |
| 首页、游戏库前端实现 | 已提供示例代码；需以实际提交和测试为准 |
| Spring Boot 基础工程与健康检查代码 | 已创建；8081 端口验证待确认 |
| MySQL / MyBatis-Plus 数据库接入 | 待开发 |
| Spring AI 攻略问答与摘要 | 待开发 |
| 前后端联调与正式部署 | 待开发 |

## 八、项目说明

本项目仅用于课程设计与学习实践。游戏信息和示例攻略应使用原创、许可使用或其他合规来源的内容；演示数据不代表真实游戏资料。AI 输出仅供参考，应展示可核验的攻略来源，并在无可靠资料时明确提示。