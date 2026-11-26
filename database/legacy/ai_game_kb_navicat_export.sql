/*
 Navicat Premium Data Transfer

 Source Server         : localhost_3307
 Source Server Type    : MySQL
 Source Server Version : 80039
 Source Host           : localhost:3307
 Source Schema         : ai_game_kb

 Target Server Type    : MySQL
 Target Server Version : 80039
 File Encoding         : 65001

 Date: 17/11/2026 17:38:53
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for ai_chat_log
-- ----------------------------
DROP TABLE IF EXISTS `ai_chat_log`;
CREATE TABLE `ai_chat_log`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `session_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '会话唯一流水号',
  `user_id` bigint(0) NOT NULL COMMENT '提问用户ID',
  `game_id` bigint(0) NULL DEFAULT NULL COMMENT '关联游戏上下文ID(可为空)',
  `role` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '消息角色: user-玩家, assistant-AI战术顾问, tool-知识库检索调用',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '对话内容',
  `referenced_guide_ids` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '本次回答引用的攻略ID列表',
  `create_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_session_time`(`session_id`, `create_time`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AI智能体对话记录表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for game_category
-- ----------------------------
DROP TABLE IF EXISTS `game_category`;
CREATE TABLE `game_category`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `game_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '游戏唯一编码(如: GAME-BF1)',
  `game_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '游戏名称',
  `genre` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '游戏类型(FPS/ARPG/策略等)',
  `platform` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '支持平台(PC/PS5/Xbox)',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '游戏简介',
  `sort_order` int(0) NOT NULL DEFAULT 0 COMMENT '排序权重',
  `create_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_game_code`(`game_code`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '游戏分区信息表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of game_category
-- ----------------------------
INSERT INTO `game_category` VALUES (1, 'GAME-BF1', '战地1 (Battlefield 1)', 'FPS', 'PC/PS/Xbox', '第一次世界大战背景第一人称射击游戏，包含步兵作战、重型装甲与空中狗斗。', 1, '2026-11-17 11:28:59');
INSERT INTO `game_category` VALUES (2, 'GAME-DS3', '黑暗之魂3 (Dark Souls III)', 'ARPG', 'PC/PS/Xbox', '高难度硬核动作角色扮演游戏，强调武器质变、属性补正与盾反时机。', 2, '2026-11-17 11:28:59');

-- ----------------------------
-- Table structure for game_guide
-- ----------------------------
DROP TABLE IF EXISTS `game_guide`;
CREATE TABLE `game_guide`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `guide_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '攻略唯一编号(雪花算法或Redis生成, 如 GD-20261117-0001)',
  `game_id` bigint(0) NOT NULL COMMENT '所属游戏ID',
  `author_id` bigint(0) NOT NULL COMMENT '投稿人ID',
  `title` varchar(150) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '攻略标题',
  `category_tag` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '攻略分类标签(BOSS打法/载具教学/武器配装/全收集)',
  `summary` varchar(300) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '攻略摘要(供AI快速检索匹配)',
  `content_md` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'Markdown格式攻略正文',
  `view_count` int(0) NOT NULL DEFAULT 0 COMMENT '浏览量(同步Redis热度榜)',
  `audit_status` tinyint(0) NOT NULL DEFAULT 0 COMMENT '入库审核状态: 0-待审核, 1-已上架(入库), 2-已驳回/下架',
  `version` int(0) NOT NULL DEFAULT 1 COMMENT '乐观锁版本号(用于缓存一致性控制)',
  `create_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '投稿时间',
  `update_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_guide_code`(`guide_code`) USING BTREE,
  INDEX `idx_game_status`(`game_id`, `audit_status`) USING BTREE,
  INDEX `idx_title`(`title`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '游戏攻略知识库主表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of game_guide
-- ----------------------------
INSERT INTO `game_guide` VALUES (1, 'GD-20261117-0001', 1, 2, '攻击机空中狗斗机动教学：High Yo-Yo 与桶滚防御', '载具教学', '详解战地1攻击机遭遇敌方战斗机咬尾时的桶滚减速反杀与高悠悠机动切入技巧。', '### 1. High Yo-Yo（高悠悠机动）\n当敌机在水平转弯半径优于你时，拉起机头向斜上方爬升，将多余速度转化为高度势能，随后在顶点滚转俯冲切入敌机内圈六点钟方向。\n\n### 2. 桶滚防御（Barrel Roll）\n被咬尾时切忌纯水平盘旋，应结合方向舵与副翼做螺旋桶滚，迫使高速追击的敌机冲过头（Overshoot），趁机切换后座机枪或反咬。', 328, 1, 1, '2026-11-17 11:28:59', '2026-11-17 11:28:59');
INSERT INTO `game_guide` VALUES (2, 'GD-20261117-0002', 2, 2, '洛斯里克骑士开局加点与盾反机制详解', '新手入门', '针对骑士开局的血量、精力加点建议，以及使用小圆盾进行弹反（Parry）的帧数判定技巧。', '### 1. 前期加点优先级\n优先将**生命值**点到 27（达到 1000 HP 软上限），**持久力**点到 20 保证翻滚与挥刀精力，武器前期推荐**粗制质变**，后期力敏双修转**熟练质变**。\n\n### 2. 盾反时机\n使用团牌或小皮盾时，不要等敌人武器砸到脸上再按弹反，必须在敌人**挥舞动作由蓄力转为前劈的瞬间**按下弹反键。', 512, 1, 1, '2026-11-17 11:28:59', '2026-11-17 11:28:59');
INSERT INTO `game_guide` VALUES (3, 'GD-20261117-0003', 2, 2, '法兰要塞毒沼泽全要素收集与深渊监视者打法', 'BOSS打法', '法兰要塞三座熄灭火焰位置指南，以及薪王深渊监视者二阶段背刺技巧（待审核示例）。', '### 灭火路线\n贴左侧墙体前进可快速找到第一座高塔，注意携带紫花苔藓球解除剧毒状态...', 15, 0, 1, '2026-11-17 11:28:59', '2026-11-17 11:28:59');

-- ----------------------------
-- Table structure for game_info
-- ----------------------------
DROP TABLE IF EXISTS `game_info`;
CREATE TABLE `game_info`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `game_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '游戏名称',
  `genre` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '游戏类型',
  `platform` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT 'PC' COMMENT '支持平台',
  `description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '游戏简介',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '游戏基础字典表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of game_info
-- ----------------------------
INSERT INTO `game_info` VALUES (1, '战地1 (Battlefield 1)', '第一人称射击 / 载具对抗', 'PC / PS / Xbox', '一战背景大型多人战场FPS，含空中狗斗与重型装甲战术', '2026-11-17 17:03:19');
INSERT INTO `game_info` VALUES (2, '黑暗之魂3 (Dark Souls III)', '动作角色扮演 (ARPG)', 'PC / PS / Xbox', '高难度硬核动作游戏，强调武器质变、属性补正与盾反机制', '2026-11-17 17:03:19');

-- ----------------------------
-- Table structure for game_item_stats
-- ----------------------------
DROP TABLE IF EXISTS `game_item_stats`;
CREATE TABLE `game_item_stats`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `game_id` bigint(0) NOT NULL COMMENT '所属游戏ID',
  `item_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '装备/武器名称',
  `item_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '类型(主武器/近战武器/盾牌/载具)',
  `damage_info` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '伤害/面板数值',
  `scaling_or_feature` varchar(150) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '属性补正/特殊机制',
  `obtain_method` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '获取方式或解锁条件',
  `create_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '导入时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_game_item`(`game_id`, `item_name`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '游戏装备与数值明细表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of game_item_stats
-- ----------------------------
INSERT INTO `game_item_stats` VALUES (1, 1, 'M1907 半自动步枪（壕沟战）', '医疗兵主武器', '近距离最高42伤害，3枪毙命', '21发大弹匣，腰射散布极小，适合近中距离突击', '医疗兵等级达到1级后使用战争债券解锁', '2026-11-17 11:28:59');
INSERT INTO `game_item_stats` VALUES (2, 2, '洛斯里克骑士直剑', '直剑', '基础物理103，致命一击110', '熟练质变后力敏双B补正，锋利质变敏捷S补正', '高墙洛斯里克骑士概率掉落，或小偷第一次外出归来购买', '2026-11-17 11:28:59');

-- ----------------------------
-- Table structure for sys_user
-- ----------------------------
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `username` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '登录账号',
  `password` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '密码',
  `nickname` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '用户昵称',
  `role` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'PLAYER' COMMENT '角色：ADMIN-管理员，PLAYER-普通玩家',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_username`(`username`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '系统用户表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_user
-- ----------------------------
INSERT INTO `sys_user` VALUES (1, 'admin', '123456', '战术指挥官(管理员)', 'ADMIN', '2026-11-17 17:03:19');
INSERT INTO `sys_user` VALUES (2, 'player1', '123456', '王牌飞行员', 'PLAYER', '2026-11-17 17:03:19');

SET FOREIGN_KEY_CHECKS = 1;
