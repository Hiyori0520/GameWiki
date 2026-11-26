# database 目录说明

> 负责人：成员 A（数据库统一维护）
> 原则：**所有人本地数据库结构必须由本目录的脚本生成，不允许手工改表后不同步。**

## 1. 快速初始化（新成员必做）

⚠️ **先确认本机 MySQL 端口**，不是所有人的机器都一样。查询方法：

```powershell
netstat -ano | findstr :3306
```

确认端口后（下面以 `3306` 为例）执行：

```powershell
mysql -h127.0.0.1 -P3306 -uroot -p < D:\Work\GameWiki\database\00_init.sql
```

或用 Navicat / IDEA Database 打开 `00_init.sql` 整段执行。

### 已经建过库的成员：执行增量脚本

`00_init.sql` 会 DROP 重建，**有数据时不要重复跑**。结构有更新时，按编号执行增量脚本：

```powershell
mysql -h127.0.0.1 -P3306 -uroot -p ai_game_kb < D:\Work\GameWiki\database\01_like_history.sql
```

| 脚本 | 内容 | 是否需要执行 |
| --- | --- | --- |
| `00_init.sql` | 全量基线：建库 + 8 张表 + 演示数据 | 新成员首次初始化 |
| `01_like_history.sql` | 新增 `guide_like`、`browse_history` 两张表 | 旧库缺少这两张表时执行 |
| `02_shared_catalog.sql` | 写入后台分类和样本，增加共享目录、用户资料、偏好、操作日志等表 | Spring Boot 启动时自动执行 |
| `03_verified_game_catalog.sql` | 补全 14 款内置游戏的资料和高清封面 | Spring Boot 启动时在 02 之后自动执行 |

全新环境先运行 `00_init.sql`，再启动后端。已有数据库直接启动新版后端即可执行 02 和 03。游戏资料补全每个库执行一次，保留已有攻略、审核状态和管理员修改。发行日期采用该作品首次公开发行的日期，封面由后端通过 `/catalog/game-covers/` 提供。发行版本和封面的来源见 [游戏资料来源](game-catalog-sources.md)。

开发环境的 Vite 将 `/api` 和 `/catalog` 代理到后端，浏览器通过 API 使用同一个 MySQL 库。部署到其他环境时由同源服务或反向代理提供这两个路径。若前端跨域访问后端，通过 `GAMEWIKI_CORS_ALLOWED_ORIGINS` 配置允许的来源，多个来源以逗号分隔。

后台默认读取数据库。需要主动演示离线界面时，可在 Vite 开发环境中显式设置 `VITE_ADMIN_DEMO=true`。

## 2. 演示账号

| 账号 | 密码 | 角色 |
| --- | --- | --- |
| `admin` | `gamewiki2026` | ADMIN（管理员，可进后台） |
| `player1` | `gamewiki2026` | PLAYER（普通玩家） |

密码以 BCrypt 密文存储，数据库里看不到明文。

> ✅ 登录已切换为 BCrypt 校验（`BCryptPasswordEncoder.matches()`），用本脚本初始化后即可直接用上面的账号登录。

## 3. 表结构一览

| 表名 | 说明 | 主要使用人 |
| --- | --- | --- |
| `sys_user` | 用户与角色、启用状态 | A（认证）、D（用户管理） |
| `game_info` | 游戏基础信息（已合并原 `game_category`） | B（游戏 CRUD）、C（游戏库/详情） |
| `game_guide` | 攻略知识库主表，含审核状态 | B（攻略 CRUD）、E（AI 检索） |
| `game_item_stats` | 武器/装备数值面板 | E（AI 查面板）、B（后台维护） |
| `game_favorite` | 用户收藏 | B（收藏接口）、C（个人中心） |
| `guide_like` | 攻略点赞，`(user_id, guide_id)` 唯一，按用户去重 | B（点赞接口）、C（攻略详情） |
| `browse_history` | 浏览历史，`(user_id, target_type, target_id)` 唯一，重复访问更新时间 | B（历史接口）、C（个人中心） |
| `ai_chat_log` | AI 对话留痕与引用溯源 | E（AI）、D（统计） |

### 关于表结构调整的说明

- **`game_category` 已删除**：它的字段（name/genre/platform/description）与 `game_info` 完全重复，且代码里从未使用。游戏分区信息统一用 `game_info.genre` 表达。
- **`game_favorite` 为新增**：B 的收藏功能之前没有对应表，这是接口开发的前置依赖。
- **`guide_like` 为新增（01 脚本）**：点赞与收藏语义不同，收藏是"存起来"，点赞是"这篇有用"；唯一索引保证一人一赞，取消点赞即删记录。
- **`browse_history` 为新增（01 脚本）**：同一用户对同一目标只留一条，写入用 `INSERT ... ON DUPLICATE KEY UPDATE create_time = NOW()`，避免历史列表被反复浏览撑爆。
- **`sys_user` 新增 `status`**：D 的用户管理页需要"启用/禁用"，登录时校验 `status = 1`。
- **`game_info` 新增 `game_code` / `cover_url` / `sort_order`**：分别用于唯一标识、C 的游戏库封面展示、后台排序。

## 4. 修改数据库的流程（重要）

1. 需要加字段 / 加表 → **先在群里说**，不要自己改本地库就完事
2. 新建增量脚本 `database/01_xxx.sql`、`02_xxx.sql`，编号递增，不要改别人的脚本
3. 只有 A 有权修改 `00_init.sql`（全量基线）
4. 改完在群里发一句"数据库有更新，执行 01_xxx.sql"，其他人拉代码后同步执行
5. 提交信息写清楚，例如 `feat(db): 攻略表增加 cover_url 字段`

**为什么这么严格**：五个人各自改库是最容易出事故的地方——你本地能跑，别人拉下来启动就报表不存在。SQL 脚本进版本库，是唯一的解法。

## 5. 已知环境约定

| 项 | 值 |
| --- | --- |
| MySQL 版本 | 8.0（脚本用了 `utf8mb4_0900_ai_ci`，5.7 不支持） |
| 端口 | 写在 `application-local.yml` 的 JDBC 串里；本机实测 **3306**，成员按自己机器填写 |
| 库名 | `ai_game_kb` |
| Redis | 6379，db 0（攻略缓存、AI 会话记忆依赖） |
| 时区 | Asia/Shanghai（JDBC 串里已配） |

> `legacy/ai_game_kb_navicat_export.sql` 是最早的 Navicat 导出备份，仅供参考，**不要用它初始化**。
