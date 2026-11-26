-- =============================================================
-- 增量脚本 01：新增点赞表与浏览历史表
-- 适用于「已经执行过 00_init.sql」的本地库，不重建、不清数据
-- 负责人：成员 A ｜ 日期：2026-11-18
-- 执行：mysql -h127.0.0.1 -P3306 -uroot -p ai_game_kb < 01_like_history.sql
-- =============================================================

USE `ai_game_kb`;

CREATE TABLE IF NOT EXISTS `guide_like` (
  `id`          bigint   NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id`     bigint   NOT NULL COMMENT '点赞人ID，对应 sys_user.id',
  `guide_id`    bigint   NOT NULL COMMENT '被点赞攻略ID，对应 game_guide.id',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '点赞时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_guide` (`user_id`, `guide_id`),
  KEY `idx_guide` (`guide_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='攻略点赞表';

CREATE TABLE IF NOT EXISTS `browse_history` (
  `id`          bigint      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id`     bigint      NOT NULL COMMENT '浏览人ID，对应 sys_user.id',
  `target_type` varchar(10) NOT NULL COMMENT '目标类型：game-游戏，guide-攻略',
  `target_id`   bigint      NOT NULL COMMENT '目标ID，按 target_type 指向 game_info 或 game_guide',
  `create_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最近浏览时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_target` (`user_id`, `target_type`, `target_id`),
  KEY `idx_user_time` (`user_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户浏览历史表';

-- 验证：SHOW TABLES LIKE '%like%'; SHOW TABLES LIKE '%history%';
