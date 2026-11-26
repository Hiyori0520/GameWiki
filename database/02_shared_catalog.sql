-- 在 00_init.sql 生成的库上执行。可重复运行，保留已有业务记录。
CREATE TABLE IF NOT EXISTS catalog_category (
  name varchar(50) NOT NULL PRIMARY KEY,
  sort_order int NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO catalog_category (name, sort_order) VALUES
 ('GAL / 视觉小说', 10), ('ADV / 冒险', 20), ('RPG / 角色扮演', 30),
 ('SLG / 模拟经营', 40), ('ACT / 动作', 50), ('ARPG / 动作角色扮演', 60),
 ('FPS / 第一人称射击', 70), ('TPS / 第三人称射击', 80), ('RTS / 即时战略', 90),
 ('TBS / 回合制策略', 100), ('SIM / 模拟', 110), ('RAC / 竞速', 120),
 ('SPG / 体育', 130), ('FTG / 格斗', 140), ('RHY / 音乐节奏', 150),
 ('PUZ / 解谜', 160), ('生存', 170), ('恐怖', 180), ('沙盒', 190),
 ('MMO / 多人在线', 200), ('其他', 210);

CREATE TABLE IF NOT EXISTS catalog_platform (
  name varchar(50) NOT NULL PRIMARY KEY,
  sort_order int NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT IGNORE INTO catalog_platform VALUES
 ('Windows', 10), ('Nintendo Switch', 20), ('PlayStation', 30),
 ('macOS', 40), ('Android', 50), ('iOS', 60), ('Linux', 70), ('Xbox', 80), ('其他平台', 90);

CREATE TABLE IF NOT EXISTS catalog_game (
  game_id bigint NOT NULL PRIMARY KEY,
  original_name varchar(160) NOT NULL DEFAULT '',
  english_name varchar(160) NOT NULL DEFAULT '',
  aliases json,
  company varchar(120) NOT NULL DEFAULT '',
  developer varchar(120) NOT NULL DEFAULT '',
  background text,
  secondary_categories json,
  tags json,
  secondary_platforms json,
  release_date date,
  rating decimal(3,1),
  votes int NOT NULL DEFAULT 0,
  status varchar(24) NOT NULL DEFAULT 'PUBLISHED',
  version int NOT NULL DEFAULT 1,
  deleted_at datetime,
  CONSTRAINT fk_catalog_game FOREIGN KEY (game_id) REFERENCES game_info(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS catalog_guide (
  guide_id bigint NOT NULL PRIMARY KEY,
  tags json,
  cover text,
  spoiler boolean NOT NULL DEFAULT false,
  featured boolean NOT NULL DEFAULT false,
  published_at datetime,
  reviewed_at datetime,
  review_reason varchar(500),
  revision_of bigint UNIQUE,
  CONSTRAINT fk_catalog_guide FOREIGN KEY (guide_id) REFERENCES game_guide(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS guide_taxonomy (
  id bigint NOT NULL AUTO_INCREMENT PRIMARY KEY,
  kind varchar(12) NOT NULL,
  name varchar(50) NOT NULL,
  UNIQUE KEY uk_guide_taxonomy (kind, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS user_profile (
  user_id bigint NOT NULL PRIMARY KEY,
  avatar varchar(255) NOT NULL DEFAULT '',
  phone varchar(24) NOT NULL DEFAULT '',
  email varchar(190) NOT NULL DEFAULT '',
  bio varchar(300) NOT NULL DEFAULT '',
  gender varchar(24) NOT NULL DEFAULT '',
  birthday date,
  location varchar(120) NOT NULL DEFAULT '',
  membership varchar(16) NOT NULL DEFAULT 'standard',
  experience int NOT NULL DEFAULT 0,
  last_login_at datetime,
  banned_until datetime,
  CONSTRAINT fk_user_profile FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS user_game_favorite (
  user_id bigint NOT NULL,
  game_id bigint NOT NULL,
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id, game_id),
  CONSTRAINT fk_user_game_favorite_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
  CONSTRAINT fk_user_game_favorite_game FOREIGN KEY (game_id) REFERENCES game_info(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS user_follow (
  user_id bigint NOT NULL,
  author_id bigint NOT NULL,
  PRIMARY KEY (user_id, author_id),
  CONSTRAINT fk_user_follow_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
  CONSTRAINT fk_user_follow_author FOREIGN KEY (author_id) REFERENCES sys_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS user_level_rule (
  level int NOT NULL PRIMARY KEY,
  name varchar(50) NOT NULL,
  required_experience int NOT NULL,
  rule varchar(200) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT IGNORE INTO user_level_rule VALUES
 (1,'初来乍到',0,'注册后获得'), (2,'探索者',100,'累计经验达到 100'),
 (3,'攻略达人',300,'累计经验达到 300'), (4,'资深玩家',600,'累计经验达到 600'),
 (5,'知识库专家',1000,'累计经验达到 1000');

CREATE TABLE IF NOT EXISTS admin_preference (
  user_id bigint NOT NULL PRIMARY KEY,
  sidebar_collapsed boolean NOT NULL DEFAULT false,
  page_size int NOT NULL DEFAULT 20,
  dashboard_period int NOT NULL DEFAULT 7,
  CONSTRAINT fk_admin_preference FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS admin_activity (
  id bigint NOT NULL AUTO_INCREMENT PRIMARY KEY,
  actor varchar(50) NOT NULL,
  module varchar(24) NOT NULL,
  action varchar(50) NOT NULL,
  target varchar(160) NOT NULL,
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS user_ban_log (
  id bigint NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id bigint NOT NULL,
  operator varchar(50) NOT NULL,
  action varchar(12) NOT NULL,
  reason varchar(500) NOT NULL DEFAULT '',
  operated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  expires_at datetime,
  INDEX idx_user_ban_log (user_id, id),
  CONSTRAINT fk_ban_log_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS catalog_media (
  id char(36) NOT NULL PRIMARY KEY,
  content_type varchar(24) NOT NULL,
  image mediumblob NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS catalog_transfer (
  id char(36) NOT NULL PRIMARY KEY,
  user_id bigint NOT NULL,
  kind varchar(10) NOT NULL,
  result json NOT NULL,
  payload mediumblob,
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_catalog_transfer_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 同一库只引入一次基线分类样本，避免重启时还原后续编辑或删除。
CREATE TABLE IF NOT EXISTS catalog_seed (
  name varchar(50) NOT NULL PRIMARY KEY
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
START TRANSACTION;
INSERT IGNORE INTO catalog_seed (name) VALUES ('admin-dashboard-catalog-v1');
SET @apply_dashboard_seed = (ROW_COUNT() = 1);
INSERT IGNORE INTO guide_taxonomy (kind,name)
SELECT t.kind,t.name FROM (
 SELECT 'category' kind,'新手入门' name
 UNION ALL SELECT 'category','全收集' UNION ALL SELECT 'category','BOSS打法'
 UNION ALL SELECT 'category','载具教学' UNION ALL SELECT 'category','武器配装'
 UNION ALL SELECT 'category','流程攻略' UNION ALL SELECT 'category','角色路线'
 UNION ALL SELECT 'category','成就收集' UNION ALL SELECT 'tag','攻略'
 UNION ALL SELECT 'tag','入门' UNION ALL SELECT 'tag','收集'
 UNION ALL SELECT 'tag','探索' UNION ALL SELECT 'tag','剧情'
) t WHERE @apply_dashboard_seed;
UPDATE game_info SET genre = 'FPS / 第一人称射击' WHERE genre = 'FPS' OR (game_code='GAME-BF1' AND genre='第一人称射击 / 载具对抗');
UPDATE game_info SET genre = 'ARPG / 动作角色扮演' WHERE genre = 'ARPG' OR (game_code='GAME-DS3' AND genre='动作角色扮演 (ARPG)');
INSERT IGNORE INTO catalog_game(game_id,secondary_platforms)
SELECT id,JSON_ARRAY('PlayStation','Xbox') FROM game_info WHERE game_code IN ('GAME-BF1','GAME-DS3') AND platform='PC / PS / Xbox';
UPDATE game_info SET platform='Windows' WHERE game_code IN ('GAME-BF1','GAME-DS3') AND platform='PC / PS / Xbox';
INSERT IGNORE INTO game_info (game_code, game_name, genre, platform, cover_url, description)
SELECT t.code, t.name, t.genre, 'Windows', t.cover, t.description
FROM (
 SELECT 'CAT-WA2' code, '白色相簿2' name, 'GAL / 视觉小说' genre, '/catalog/cover-1.jpg' cover, '围绕校园乐队展开的剧情视觉小说。' description
 UNION ALL SELECT 'CAT-CLANNAD','CLANNAD','GAL / 视觉小说','/catalog/cover-2.jpg','以校园、日常和家庭关系为主题的视觉小说。'
 UNION ALL SELECT 'CAT-MAHOYO','魔法使之夜','GAL / 视觉小说','/catalog/cover-3.jpg','描写魔术师日常与奇幻事件的视觉小说。'
 UNION ALL SELECT 'CAT-AOKANA','苍之彼方的四重奏','GAL / 视觉小说','/catalog/cover-4.jpg','围绕空中竞技与校园生活展开。'
 UNION ALL SELECT 'CAT-SENREN','千恋＊万花','GAL / 视觉小说','/catalog/cover-5.jpg','以和风小镇与传说为背景的视觉小说。'
 UNION ALL SELECT 'CAT-SUMMER','Summer Pockets','GAL / 视觉小说','/catalog/cover-6.jpg','以海岛暑假为背景的剧情游戏。'
 UNION ALL SELECT 'CAT-STELLA','星之终途','GAL / 视觉小说',NULL,'以科幻旅程为主题的视觉小说。'
 UNION ALL SELECT 'CAT-GRIS','GRIS','ADV / 冒险',NULL,'以平台跳跃和场景解谜推动探索的冒险游戏。'
 UNION ALL SELECT 'CAT-HOLLOW','空洞骑士','ACT / 动作',NULL,'围绕地下王国展开的探索与动作游戏。'
 UNION ALL SELECT 'CAT-STARDEW','星露谷物语','SLG / 模拟经营',NULL,'经营农场并探索社区生活。'
 UNION ALL SELECT 'CAT-ACE','逆转裁判123','ADV / 冒险',NULL,'收集证物并在法庭上揭示证词矛盾。'
 UNION ALL SELECT 'CAT-P5R','女神异闻录5 皇家版','RPG / 角色扮演',NULL,'结合日常互动与回合制战斗的角色扮演游戏。'
) t WHERE @apply_dashboard_seed;

INSERT IGNORE INTO catalog_game (game_id, company, tags, release_date, status)
SELECT g.id, t.company, t.tags, t.release_date, t.status
FROM game_info g JOIN (
 SELECT 'CAT-WA2' code, 'Leaf' company, JSON_ARRAY('恋爱','校园','音乐') tags, '2010-03-26' release_date, 'PUBLISHED' status
 UNION ALL SELECT 'CAT-CLANNAD','Visual Arts / Key',JSON_ARRAY('校园','日常'),'2004-04-28','PUBLISHED'
 UNION ALL SELECT 'CAT-MAHOYO','TYPE-MOON',JSON_ARRAY('奇幻','悬疑'),'2012-04-12','PUBLISHED'
 UNION ALL SELECT 'CAT-AOKANA','sprite',JSON_ARRAY('校园','运动'),'2014-11-28','PUBLISHED'
 UNION ALL SELECT 'CAT-SENREN','Yuzusoft',JSON_ARRAY('恋爱','和风'),'2016-07-29','PUBLISHED'
 UNION ALL SELECT 'CAT-SUMMER','Visual Arts / Key',JSON_ARRAY('夏日','剧情'),'2018-06-29','PUBLISHED'
 UNION ALL SELECT 'CAT-STELLA','Visual Arts / Key',JSON_ARRAY('科幻','旅行'),'2022-09-30','PENDING_REVIEW'
 UNION ALL SELECT 'CAT-GRIS','Devolver Digital',JSON_ARRAY('解谜','探索'),'2018-12-13','PUBLISHED'
 UNION ALL SELECT 'CAT-HOLLOW','Team Cherry',JSON_ARRAY('探索','奇幻'),'2017-02-24','PUBLISHED'
 UNION ALL SELECT 'CAT-STARDEW','ConcernedApe',JSON_ARRAY('种植','经营'),'2016-02-26','PUBLISHED'
 UNION ALL SELECT 'CAT-ACE','Capcom',JSON_ARRAY('推理','悬疑'),'2019-02-21','OFF_SHELF'
 UNION ALL SELECT 'CAT-P5R','ATLUS',JSON_ARRAY('回合制','校园'),'2019-10-31','PENDING_REVIEW'
) t ON t.code=g.game_code WHERE @apply_dashboard_seed;

INSERT IGNORE INTO game_guide (guide_code, game_id, author_id, title, category_tag, summary, content_md, audit_status)
SELECT t.code, g.id, u.id, t.title, '新手入门', t.summary, t.body, 1
FROM (
 SELECT 'CAT-GD-WA2' code, 'CAT-WA2' game_code, '白色相簿2 新手阅读指南' title,
  '从序章开始，并做好分支存档。' summary,
  CONCAT('## 阅读顺序', CHAR(10), '先完成序章，再进入后续章节。', CHAR(10), CHAR(10), '## 分支管理', CHAR(10), '- 在重要选择前保留独立存档。', CHAR(10), '- 完成一条路线后再整理其他分支。') body
 UNION ALL SELECT 'CAT-GD-GRIS','CAT-GRIS','GRIS 探索入门','观察场景中的色彩和路径变化。',
  CONCAT('## 识别路径',CHAR(10),'遇到障碍时先辨认可互动地形，再回看刚刚经过的区域。')
 UNION ALL SELECT 'CAT-GD-HOLLOW','CAT-HOLLOW','空洞骑士 探索准备','用地图和长椅管理探索路线。',
  CONCAT('## 探索与返回',CHAR(10),'先寻找地图商人和可休息的长椅。遇到暂时无法通过的路径时做标记，获得能力后再返回。')
 UNION ALL SELECT 'CAT-GD-STARDEW','CAT-STARDEW','星露谷物语 农场第一周','从小规模种植开始，留出钓鱼和探索时间。',
  CONCAT('## 安排一天',CHAR(10),'- 先给作物浇水，再消耗剩余体力。',CHAR(10),'- 购买种子时注意成熟天数和季节。')
) t JOIN game_info g ON g.game_code=t.game_code
JOIN sys_user u ON u.username='admin' WHERE @apply_dashboard_seed;
COMMIT;

-- 把既有的分类和标签并入字典，保留此前创建的内容。
INSERT IGNORE INTO catalog_category (name, sort_order)
SELECT DISTINCT genre, 999 FROM game_info WHERE genre <> '';
INSERT IGNORE INTO catalog_platform (name, sort_order)
SELECT DISTINCT platform, 999 FROM game_info WHERE platform <> '';
INSERT IGNORE INTO guide_taxonomy (kind, name)
SELECT DISTINCT 'category', category_tag FROM game_guide WHERE category_tag <> '';
