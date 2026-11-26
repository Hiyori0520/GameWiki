# GameWiki API 契约

> 维护人：成员 A ｜ 版本：v1 ｜ 生效日期：2026-11-17
> **本文件是前后端唯一的接口约定。C/D 按本文写前端，B/E 按本文写后端。**
> 任何字段改名、路径调整，必须先改本文 + 群里通知，再改代码。

---

## 一、统一响应结构

所有接口（含错误）一律返回 HTTP 200，业务状态放在 `code` 里：

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `code` | int | 业务状态码，见下表 |
| `message` | string | 提示文案。成功默认 `success`（个别业务可自定义，如"投稿成功，等待审核入库"），失败为可直接展示给用户的中文 |
| `data` | any | 业务数据；无返回值时为 `null` |

> ⚠️ **字段名是 `message`，不是 `msg`。** 原 `Result.java` 用的是 `msg`，本次已统一改为 `message`，前端 `request.js` 同步更新。

### 状态码

| code | 含义 | 前端处理 |
| --- | --- | --- |
| 200 | 成功 | 正常取 `data` |
| 400 | 参数错误 / 业务校验失败 | 弹 `message` 提示 |
| 401 | 未登录 / token 失效 | 清本地 token，跳 `/login` |
| 403 | 已登录但无权限（如玩家访问后台接口） | 弹"无访问权限" |
| 404 | 接口或资源不存在 | 弹 `message` |
| 500 | 服务端异常 | 弹 `message`，控制台看日志 |

---

## 二、分页规范

**请求参数**（`GET` 的 query string）：

| 参数 | 类型 | 默认 | 说明 |
| --- | --- | --- | --- |
| `page` | int | 1 | 页码，**从 1 开始** |
| `size` | int | 10 | 每页条数，上限 100 |

**响应 `data` 结构**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "records": [],
    "total": 42,
    "page": 1,
    "size": 10
  }
}
```

| 字段 | 说明 |
| --- | --- |
| `records` | 当前页数据数组 |
| `total` | 总条数（用于分页器） |
| `page` | 当前页码 |
| `size` | 每页条数 |

> 不分页的接口 `data` 直接是数组或对象，不要套 `records`。

---

## 三、鉴权规范

- 登录成功后后端返回 `token`，前端存 `localStorage`
- 之后所有请求带上请求头：

```
Authorization: <token>
```

（直接放 token 值，不加 `Bearer` 前缀——与现有 `frontend/src/utils/request.js` 保持一致，避免前端返工）

- 公开接口（无需 token）：`/api/user/login`、`/api/user/register`
- token 有效期 12 小时，登出即失效

**权限矩阵**：

| 范围 | PLAYER | ADMIN |
| --- | --- | --- |
| 浏览游戏、攻略，AI 问答 | ✅ | ✅ |
| 投稿攻略、收藏 | ✅ | ✅ |
| 工作台统计 `/api/dashboard/overview` | ✅ | ✅ |
| 攻略审核、上下架、删除 | ❌ | ✅ |
| 基础数据增删改、用户管理 | ❌ | ✅ |

> 实现说明：拦截器为 `/api/**` 全量校验，仅 `login`、`register` 放行；其余接口默认要求登录，
> 标了 `@RequireRole("ADMIN")` 的才校验角色。工作台对所有登录用户开放，避免玩家登录后首页直接 403。

**参数校验约定**：请求体统一用 `dto` 包下的对象接收并加 `@Valid`，校验注解（`@NotBlank`/`@Size`/`@NotNull`）写在 DTO 字段上，
校验失败由全局异常处理器返回 `400 + 具体提示`。接口文档与示例见 `docs/后端开发规范.md`。

---

## 四、路径与字段命名规范

1. 统一前缀 `/api`，资源用**复数名词**：`/api/games`、`/api/guides`
2. 动作用 HTTP 方法表达，不在路径里写 `getXxx`/`saveXxx`：
   - `GET /api/games` 列表　`GET /api/games/{id}` 详情
   - `POST /api/games` 新增　`PUT /api/games/{id}` 修改　`DELETE /api/games/{id}` 删除
3. JSON 字段用 **camelCase**（`guideCount`、`createTime`），数据库用蛇形，由 MyBatis-Plus 自动映射
4. 时间统一字符串 `yyyy-MM-dd HH:mm:ss`
5. 分页列表统一用 `page`/`size`，禁止 `current`/`pageNum`/`limit` 混用

---

## 五、接口清单

### 5.1 已完成（可直接联调）

| 方法 | 路径 | 说明 | 负责人 | 权限 |
| --- | --- | --- | --- | --- |
| POST | `/api/user/login` | 登录，返回 token + userInfo | A | 公开 |
| POST | `/api/user/register` | 注册（密码 BCrypt 加密） | A | 公开 |
| POST | `/api/user/logout` | 登出，删除 Redis token | A | 登录 |
| GET | `/api/user/info` | 当前登录人信息 | A | 登录 |
| PUT | `/api/user/password` | 修改密码（`{oldPassword, newPassword}`） | A | 登录 |
| POST | `/api/ai/chat` | AI 问答（`{sessionId, question}`） | E | 登录 |
| GET | `/api/dashboard/overview` | 后台统计 + 图表数据 | D | 登录 |
| GET | `/api/guide/{id}` | 攻略详情（走 Redis 缓存） | B | 登录 |
| GET | `/api/guide/search` | 攻略检索（按 gameId / keyword） | B | 登录 |
| GET | `/api/guide/all` | 全量攻略（含待审核） | B | **ADMIN** |
| DELETE | `/api/guide/{id}` | 删除攻略 | B | **ADMIN** |

> 鉴权已实测通过：未带 token → 401；PLAYER 访问 ADMIN 接口 → 403；登出后旧 token 立即失效。

### 5.2 过渡接口（⚠️ 保留一个迭代，B 完成后废弃）

| 方法 | 旧路径 | 新路径 |
| --- | --- | --- |
| GET | `/api/guide/all` | `/api/admin/guides` |
| POST | `/api/guide/submit` | `/api/guides` |
| PUT | `/api/guide/update` | `/api/guides/{id}` |
| DELETE | `/api/guide/{id}` | 不变 |
| GET | `/api/basic/games` | `/api/games` |
| POST | `/api/basic/games/save` | `/api/games` 或 `/api/games/{id}` |
| GET/POST/DELETE | `/api/basic/items` | `/api/admin/items` |

> 过渡期内 B 不要直接删旧接口，等 C/D 前端全部切到新路径再清理。

### 5.3 待开发（契约草案，由对应负责人确认后锁定）

**成员 A**

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| PUT | `/api/users/me` | 修改资料（昵称/邮箱/手机/所在地/简介）。⚠️ 需先给 `sys_user` 增补 `email`、`phone`、`location`、`bio` 四个字段，字段加完再实现 |

**成员 B**

| 方法 | 路径 | 关键参数 |
| --- | --- | --- |
| GET | `/api/games` | `page` `size` `keyword` `genre` `sort`(latest/hot) |
| GET | `/api/games/{id}` | — |
| GET | `/api/games/{id}/guides` | `page` `size` |
| GET | `/api/guides` | `page` `size` `gameId` `keyword` `categoryTag` |
| GET | `/api/guides/{id}` | — |
| POST | `/api/guides` | 投稿，`author_id` 从 token 取，不要前端传 |
| POST | `/api/guides/{id}/favorite` | 收藏（依赖 `game_favorite` 表） |
| DELETE | `/api/guides/{id}/favorite` | 取消收藏 |
| GET | `/api/users/me/favorites` | `page` `size` |
| POST | `/api/guides/{id}/like` | 点赞（依赖 `guide_like` 表，唯一索引去重） |
| DELETE | `/api/guides/{id}/like` | 取消点赞 |
| GET | `/api/users/me/history` | 浏览历史，`page` `size`（依赖 `browse_history` 表） |
| DELETE | `/api/users/me/history` | 清空历史 |

**攻略正文格式（B 必读）**

`game_guide.content_md` 存 **Markdown 原文**，接口原样返回，后端不做 HTML 转换。
前端统一用 `markdown-it` 渲染（组件：`frontend/src/components/MarkdownView.vue`）。
不要再把正文拆成结构化 `sections` 数组——那是 C 早期页面的方案，已作废。

**浏览历史写入时机**：`GET /api/guides/{id}`、`GET /api/games/{id}` 命中时，若请求带有效 token，
异步写一条 `browse_history`（`INSERT ... ON DUPLICATE KEY UPDATE create_time = NOW()`）；
未登录不写，不影响接口返回。


**成员 E**

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/ai/chat` | AI 问答（`{sessionId, question}`），返回 `{sessionId, reply}` |
| POST | `/api/ai/chat/stream` | AI 问答（流式），`text/event-stream`，事件依次为 `session` → `chunk`* → `done`/`error`，`data` 为 JSON 字符串 |
| GET | `/api/ai/sessions` | 当前用户的会话列表：`sessionId` `title` `startedAt` `lastAt` `messageCount` |
| GET | `/api/ai/sessions/{sessionId}` | 某个会话的全部消息：`id` `role`(user/ai) `content` `createTime` |
| DELETE | `/api/ai/sessions/{sessionId}` | 删除会话及其消息 |
| POST | `/api/ai/summary` | 🚧 待开发：生成攻略摘要（管理员确认后保存） |

**成员 D（管理端与后台接口，已完成，统一前缀 `/api/admin/v1`，均要求 `ADMIN` 角色；分页参数为 `page` / `pageSize`，返回 `{ records, total, page, pageSize }`）**

*会话、偏好与后台总览*

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/admin/v1/session` | 当前管理员身份、昵称及 17 项细粒度权限列表 |
| GET | `/api/admin/v1/preferences` | 读取管理员偏好（`sidebarCollapsed` / `pageSize` / `dashboardPeriod`） |
| PUT | `/api/admin/v1/preferences` | 保存管理员偏好（`pageSize` ∈ 20/50/100，`dashboardPeriod` ∈ 7/30/90） |
| GET | `/api/admin/v1/dashboard/summary` | 游戏/攻略/用户/内容总量、近 7 日增量与待审核攻略数 |
| GET | `/api/admin/v1/dashboard/trends` | 7/30/90 天游戏与攻略新增趋势（`days`） |
| GET | `/api/admin/v1/dashboard/game-distribution` | 游戏主分类数量分布 |
| GET | `/api/admin/v1/dashboard/recent` | 最新内容列表（`type`=games/guides/users，`limit`） |
| GET | `/api/admin/v1/dashboard/logs` | 管理员操作审计日志（`limit`） |
| GET | `/api/admin/v1/dashboard/health` | API 与数据库健康状态 |

*游戏管理与批量导入导出*

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/admin/v1/games` | 游戏管理分页查询（关键词、分类、平台、厂商、状态、排序） |
| GET | `/api/admin/v1/games/options` | 分类、平台与厂商筛选项 |
| GET | `/api/admin/v1/games/stats` | 游戏各状态统计与近 7 日增量 |
| GET | `/api/admin/v1/games/{id}` | 游戏管理详情（含乐观锁 `version`） |
| POST | `/api/admin/v1/games` | 新增游戏 |
| PUT | `/api/admin/v1/games/{id}` | 编辑游戏资料 |
| DELETE | `/api/admin/v1/games/{id}` | 软删除游戏（有关联攻略时拦截） |
| POST | `/api/admin/v1/games/{id}/{action}` | 游戏状态流转：`submit-review` / `publish` / `off-shelf` / `reject` |
| POST | `/api/admin/v1/games/batch` | 批量操作（`publish` / `off-shelf` / `delete` / `add-tags` / `set-categories`，最多 1000 条） |
| POST | `/api/admin/v1/uploads/images` | 图片上传（≤1 MB，JPG/PNG/WebP 魔数校验），返回 `/catalog/media/{id}` |
| GET | `/catalog/media/{id}` | 读取已上传图片二进制内容（公开缓存，非 `/api/admin/v1` 前缀） |
| POST | `/api/admin/v1/imports/games` | 上传 CSV/XLSX 批量导入游戏（1–1000 条） |
| POST | `/api/admin/v1/exports/games` | 按勾选 ID 或查询条件创建游戏导出任务 |
| GET | `/api/admin/v1/{imports\|exports}/games/{id}` | 查询当前管理员导入/导出任务状态与汇总统计 |
| GET | `/api/admin/v1/{imports\|exports}/games/{id}/download` | 下载导入错误报告或导出结果数据 |

*攻略管理、审核台与分类标签*

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/admin/v1/guides` | 全量攻略检索、筛选、排序与分页 |
| GET | `/api/admin/v1/guide-reviews` | 待审核攻略队列分页（审核台） |
| GET | `/api/admin/v1/guides/stats` | 攻略各状态数量、今日/本周新增、本周发布与总阅读量 |
| GET | `/api/admin/v1/guides/export` | 按条件或审核队列导出攻略 JSON |
| POST | `/api/admin/v1/guides/import` | 事务批量导入攻略（1–500 条） |
| GET | `/api/admin/v1/guides/{id}` | 攻略详情（`forEdit=true` 返回可编辑修订内容） |
| POST | `/api/admin/v1/guides` | 新建攻略 |
| PUT | `/api/admin/v1/guides/{id}` | 编辑攻略（已发布攻略编辑进入待审修订版本） |
| DELETE | `/api/admin/v1/guides/{id}` | 删除攻略 |
| POST | `/api/admin/v1/guides/{id}/{action}` | 攻略状态流转：`submit-review` / `off-shelf` |
| POST | `/api/admin/v1/guide-reviews/{id}/{action}` | 攻略审核：`approve` / `reject`（可附 `reason`） |
| GET | `/api/admin/v1/guide-{categories\|tags}` | 查询攻略分类或标签字典 |
| POST | `/api/admin/v1/guide-{categories\|tags}` | 新增攻略分类或标签 |
| DELETE | `/api/admin/v1/guide-{categories\|tags}/{id}` | 删除攻略分类或标签 |

*用户管理与账号治理*

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/admin/v1/users` | 用户列表检索、角色/会员/状态筛选与分页 |
| GET | `/api/admin/v1/users/{id}` | 用户详情（含扩展资料、脱敏邮箱与封禁历史） |
| GET | `/api/admin/v1/users/stats` | 用户总量、近 7 日新增/活跃、VIP 与封禁统计 |
| GET | `/api/admin/v1/users/growth` | 7/30/90 天注册增长趋势（`days`） |
| GET | `/api/admin/v1/users/distribution` | 会员等级分布 |
| GET | `/api/admin/v1/users/active-ranking` | 活跃贡献排行 |
| GET | `/api/admin/v1/user-levels` | 用户成长等级规则列表 |
| PATCH | `/api/admin/v1/users/{id}/profile` | 管理员修改用户昵称 |
| POST | `/api/admin/v1/users/{id}/ban` | 封禁用户（支持原因与过期时间）并写封禁审计日志 |
| POST | `/api/admin/v1/users/{id}/unban` | 解封用户并写审计日志 |
| POST | `/api/admin/v1/users/{id}/password` | 管理员重置密码，body 可选 `{newPassword}`；不传则生成 10 位随机密码，明文仅在本次响应返回 |

> 完整管理端页面、权限矩阵与共享目录说明见 [`docs/管理端与后台接口说明.md`](管理端与后台接口说明.md)。

---

## 六、示例

### 游戏列表（C 的游戏库页面直接对接）

```
GET /api/games?page=1&size=10&keyword=&genre=全部&sort=latest
```

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "records": [
      {
        "id": 1,
        "gameCode": "GAME-BF1",
        "name": "战地1 (Battlefield 1)",
        "cover": "/uploads/game-bf1.jpg",
        "category": "第一人称射击",
        "guideCount": 12,
        "createTime": "2026-11-17 11:28:59"
      }
    ],
    "total": 2,
    "page": 1,
    "size": 10
  }
}
```

### 登录

```
POST /api/user/login
{"username": "admin", "password": "gamewiki2026"}
```

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "token": "8f3c...",
    "userInfo": {
      "id": 1,
      "username": "admin",
      "nickname": "战术指挥官(管理员)",
      "role": "ADMIN"
    }
  }
}
```

### 无权限

```json
{ "code": 403, "message": "无访问权限", "data": null }
```

---

## 七、变更流程

1. 想加接口 / 改字段 → 先在本文件提改动，群里同步
2. 改完本文再改代码，**不要先改代码再补文档**
3. 前端如遇 `code` 不认识，按 500 兜底处理并找 A
4. 本文由 A 维护，其他人提 PR 修改需说明原因
