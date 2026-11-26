-- =============================================================
-- GameWiki / AI 游戏攻略知识库  数据库初始化脚本
-- 文件：database/00_init.sql
-- 负责人：成员 A（数据库统一维护）
-- 适用：MySQL 8.0+
-- 说明：本脚本可重复执行（DROP + CREATE），执行后即可直接启动后端
-- 变更记录：
--   2026-11-17 v1  合并 game_category 到 game_info；新增 game_favorite；
--                  密码改为 BCrypt 密文；补充索引与 status 字段
-- =============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE DATABASE IF NOT EXISTS `ai_game_kb`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

USE `ai_game_kb`;

-- -------------------------------------------------------------
-- 1. 系统用户表
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user` (
  `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `username`    varchar(50)  NOT NULL                COMMENT '登录账号',
  `password`    varchar(100) NOT NULL                COMMENT 'BCrypt 密文，禁止存明文',
  `nickname`    varchar(50)  NOT NULL                COMMENT '用户昵称',
  `role`        varchar(20)  NOT NULL DEFAULT 'PLAYER' COMMENT '角色：ADMIN-管理员，PLAYER-普通玩家',
  `status`      tinyint      NOT NULL DEFAULT 1      COMMENT '状态：1-正常，0-禁用',
  `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
  `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统用户表';

-- -------------------------------------------------------------
-- 2. 游戏基础信息表
--    原 game_category 与本表字段重复，已合并，游戏分区即本表
--    game_guide.game_id 指向本表 id
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `game_info`;
CREATE TABLE `game_info` (
  `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `game_code`   varchar(32)  NULL                    COMMENT '游戏唯一编码，如 GAME-BF1',
  `game_name`   varchar(100) NOT NULL                COMMENT '游戏名称',
  `genre`       varchar(50)  NOT NULL                COMMENT '游戏类型：FPS / ARPG / 策略等',
  `platform`    varchar(50)  NOT NULL DEFAULT 'PC'    COMMENT '支持平台：PC / PS / Xbox',
  `cover_url`   varchar(255) NULL                    COMMENT '封面图地址，前端游戏库展示用',
  `description` varchar(500) NULL                    COMMENT '游戏简介',
  `sort_order`  int          NOT NULL DEFAULT 0      COMMENT '排序权重，越小越靠前',
  `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_game_code` (`game_code`),
  KEY `idx_genre_sort` (`genre`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='游戏基础信息表';

-- -------------------------------------------------------------
-- 3. 游戏攻略知识库主表
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `game_guide`;
CREATE TABLE `game_guide` (
  `id`              bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `guide_code`      varchar(32)  NOT NULL COMMENT '攻略唯一编号，Redis INCR 生成，如 GD-20261117-0001',
  `game_id`         bigint       NOT NULL COMMENT '所属游戏ID，对应 game_info.id',
  `author_id`       bigint       NOT NULL COMMENT '投稿人ID，对应 sys_user.id',
  `title`           varchar(150) NOT NULL COMMENT '攻略标题',
  `category_tag`    varchar(50)  NOT NULL COMMENT '分类标签：BOSS打法/载具教学/武器配装/全收集/新手入门',
  `summary`         varchar(300) NULL     COMMENT '摘要，供 AI 快速检索匹配',
  `content_md`      longtext     NOT NULL COMMENT 'Markdown 格式攻略正文',
  `view_count`      int          NOT NULL DEFAULT 0 COMMENT '浏览量，同步 Redis 热度榜',
  `audit_status`    tinyint      NOT NULL DEFAULT 0 COMMENT '审核状态：0-待审核，1-已上架，2-已驳回/下架',
  `version`         int          NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
  `create_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '投稿时间',
  `update_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_guide_code` (`guide_code`),
  KEY `idx_game_status` (`game_id`, `audit_status`),
  KEY `idx_status_time` (`audit_status`, `create_time`),
  KEY `idx_title` (`title`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='游戏攻略知识库主表';

-- -------------------------------------------------------------
-- 4. 游戏装备与数值明细表
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `game_item_stats`;
CREATE TABLE `game_item_stats` (
  `id`                 bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `game_id`            bigint       NOT NULL COMMENT '所属游戏ID，对应 game_info.id',
  `item_name`          varchar(100) NOT NULL COMMENT '装备/武器名称',
  `item_type`          varchar(50)  NOT NULL COMMENT '类型：主武器/近战武器/盾牌/载具',
  `damage_info`        varchar(100) NULL     COMMENT '伤害/面板数值',
  `scaling_or_feature` varchar(150) NULL     COMMENT '属性补正/特殊机制',
  `obtain_method`      varchar(255) NULL     COMMENT '获取方式或解锁条件',
  `create_time`        datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '导入时间',
  PRIMARY KEY (`id`),
  KEY `idx_game_item` (`game_id`, `item_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='游戏装备与数值明细表';

-- -------------------------------------------------------------
-- 5. 用户收藏表（成员 B 的收藏功能依赖本表）
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `game_favorite`;
CREATE TABLE `game_favorite` (
  `id`          bigint   NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id`     bigint   NOT NULL COMMENT '收藏人ID，对应 sys_user.id',
  `guide_id`    bigint   NOT NULL COMMENT '被收藏攻略ID，对应 game_guide.id',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_guide` (`user_id`, `guide_id`),
  KEY `idx_guide` (`guide_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户攻略收藏表';

-- -------------------------------------------------------------
-- 5.1 攻略点赞表
--     与收藏的区别：收藏是"存起来以后看"，点赞是"这篇有用"
--     唯一索引保证同一用户对同一攻略只能点一次（取消点赞即删除记录）
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `guide_like`;
CREATE TABLE `guide_like` (
  `id`          bigint   NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id`     bigint   NOT NULL COMMENT '点赞人ID，对应 sys_user.id',
  `guide_id`    bigint   NOT NULL COMMENT '被点赞攻略ID，对应 game_guide.id',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '点赞时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_guide` (`user_id`, `guide_id`),
  KEY `idx_guide` (`guide_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='攻略点赞表';

-- -------------------------------------------------------------
-- 5.2 浏览历史表
--     同一用户对同一目标只保留一条，再次访问时更新 create_time
--     （插入时用 ON DUPLICATE KEY UPDATE create_time = NOW()）
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `browse_history`;
CREATE TABLE `browse_history` (
  `id`          bigint      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id`     bigint      NOT NULL COMMENT '浏览人ID，对应 sys_user.id',
  `target_type` varchar(10) NOT NULL COMMENT '目标类型：game-游戏，guide-攻略',
  `target_id`   bigint      NOT NULL COMMENT '目标ID，按 target_type 指向 game_info 或 game_guide',
  `create_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最近浏览时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_target` (`user_id`, `target_type`, `target_id`),
  KEY `idx_user_time` (`user_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户浏览历史表';

-- -------------------------------------------------------------
-- 6. AI 对话记录表
--    Redis 只存最近 10 条会话记忆，本表用于完整留痕与引用溯源
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `ai_chat_log`;
CREATE TABLE `ai_chat_log` (
  `id`                   bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `session_id`           varchar(64)  NOT NULL COMMENT '会话唯一流水号',
  `user_id`              bigint       NOT NULL COMMENT '提问用户ID',
  `game_id`              bigint       NULL     COMMENT '关联游戏上下文ID，可为空',
  `role`                 varchar(20)  NOT NULL COMMENT '消息角色：user / assistant / tool',
  `content`              text         NOT NULL COMMENT '对话内容',
  `referenced_guide_ids` varchar(100) NULL     COMMENT '本次回答引用的攻略ID列表，逗号分隔',
  `create_time`          datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
  PRIMARY KEY (`id`),
  KEY `idx_session_time` (`session_id`, `create_time`),
  KEY `idx_user_time` (`user_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI 智能体对话记录表';

-- =============================================================
-- 种子数据
-- 演示账号密码统一为：gamewiki2026（BCrypt 密文存储）
-- =============================================================

INSERT INTO `sys_user` (`id`, `username`, `password`, `nickname`, `role`, `status`) VALUES
(1, 'admin',   '$2b$10$c7FeO/GFMB779/LTc1Qjjex78zc2Yxw1Q90PbiaXz3O4SIUrKwRnK', '战术指挥官(管理员)', 'ADMIN',  1),
(2, 'player1', '$2b$10$fGl2f06DgNvlCuwIJX97a.7WWtYyMlTNvyxFCSdLEJ04jacSxUj5y', '王牌飞行员',         'PLAYER', 1);

INSERT INTO `game_info` (`id`, `game_code`, `game_name`, `genre`, `platform`, `cover_url`, `description`, `sort_order`) VALUES
(1, 'GAME-BF1', '战地1 (Battlefield 1)',        '第一人称射击 / 载具对抗', 'PC / PS / Xbox', '/uploads/game-bf1.jpg', '一战背景大型多人战场 FPS，含空中狗斗与重型装甲战术', 1),
(2, 'GAME-DS3', '黑暗之魂3 (Dark Souls III)',   '动作角色扮演 (ARPG)',     'PC / PS / Xbox', '/uploads/game-ds3.jpg', '高难度硬核动作游戏，强调武器质变、属性补正与盾反机制', 2);

INSERT INTO `game_guide` (`id`, `guide_code`, `game_id`, `author_id`, `title`, `category_tag`, `summary`, `content_md`, `view_count`, `audit_status`, `version`) VALUES
(1, 'GD-20261117-0001', 1, 2, '攻击机空中狗斗机动教学：High Yo-Yo 与桶滚防御', '载具教学',
 '详解战地1攻击机遭遇敌方战斗机咬尾时的桶滚减速反杀与高悠悠机动切入技巧。',
 '### 1. High Yo-Yo（高悠悠机动）\n当敌机在水平转弯半径优于你时，拉起机头向斜上方爬升，将多余速度转化为高度势能，随后在顶点滚转俯冲切入敌机内圈六点钟方向。\n\n### 2. 桶滚防御（Barrel Roll）\n被咬尾时切忌纯水平盘旋，应结合方向舵与副翼做螺旋桶滚，迫使高速追击的敌机冲过头（Overshoot），趁机切换后座机枪或反咬。',
 328, 1, 1),
(2, 'GD-20261117-0002', 2, 2, '洛斯里克骑士开局加点与盾反机制详解', '新手入门',
 '针对骑士开局的血量、精力加点建议，以及使用小圆盾进行弹反（Parry）的帧数判定技巧。',
 '### 1. 前期加点优先级\n优先将**生命值**点到 27（达到 1000 HP 软上限），**持久力**点到 20 保证翻滚与挥刀精力，武器前期推荐**粗制质变**，后期力敏双修转**熟练质变**。\n\n### 2. 盾反时机\n使用团牌或小皮盾时，不要等敌人武器砸到脸上再按弹反，必须在敌人**挥舞动作由蓄力转为前劈的瞬间**按下弹反键。',
 512, 1, 1),
(3, 'GD-20261117-0003', 2, 2, '法兰要塞毒沼泽全要素收集与深渊监视者打法', 'BOSS打法',
 '法兰要塞三座熄灭火焰位置指南，以及薪王深渊监视者二阶段背刺技巧（待审核示例）。',
 '### 灭火路线\n贴左侧墙体前进可快速找到第一座高塔，注意携带紫花苔藓球解除剧毒状态...',
 15, 0, 1);

INSERT INTO `game_item_stats` (`id`, `game_id`, `item_name`, `item_type`, `damage_info`, `scaling_or_feature`, `obtain_method`) VALUES
(1, 1, 'M1907 半自动步枪（壕沟战）', '医疗兵主武器', '近距离最高42伤害，3枪毙命', '21发大弹匣，腰射散布极小，适合近中距离突击', '医疗兵等级达到1级后使用战争债券解锁'),
(2, 2, '洛斯里克骑士直剑', '直剑', '基础物理103，致命一击110', '熟练质变后力敏双B补正，锋利质变敏捷S补正', '高墙洛斯里克骑士概率掉落，或小偷第一次外出归来购买');

INSERT INTO `game_favorite` (`user_id`, `guide_id`) VALUES
(2, 1);

SET FOREIGN_KEY_CHECKS = 1;

-- =============================================================
-- 执行完成。演示账号：
--   admin   / gamewiki2026  （ADMIN  管理员）
--   player1 / gamewiki2026  （PLAYER 普通玩家）
-- 注意：登录接口目前仍是明文比对，需随「认证模块改造」一起切换为 BCrypt.matches()
-- =============================================================
