-- 在 02_shared_catalog.sql 后执行。仅补全尚未由管理员修改的内置条目。
-- 发行版本与原始封面的出处见 game-catalog-sources.md。
START TRANSACTION;
INSERT IGNORE INTO catalog_seed (name) VALUES ('verified-game-catalog-v1');
SET @apply_verified_catalog = (ROW_COUNT() = 1);

INSERT IGNORE INTO catalog_game (game_id)
SELECT id FROM game_info
WHERE @apply_verified_catalog AND game_code IN (
  'GAME-BF1', 'GAME-DS3', 'CAT-WA2', 'CAT-CLANNAD', 'CAT-MAHOYO', 'CAT-AOKANA', 'CAT-SENREN',
  'CAT-SUMMER', 'CAT-STELLA', 'CAT-GRIS', 'CAT-HOLLOW', 'CAT-STARDEW', 'CAT-ACE', 'CAT-P5R'
);

UPDATE game_info g
JOIN catalog_game c ON c.game_id = g.id
JOIN (
  SELECT 'GAME-BF1' AS code, 'Battlefield 1' AS original_name, 'Battlefield 1' AS english_name,
    JSON_ARRAY('BF1') AS aliases, 'Electronic Arts' AS company, 'DICE' AS developer,
    '2016-10-21' AS release_date,
    JSON_ARRAY('Windows', 'PlayStation', 'Xbox') AS platforms,
    JSON_ARRAY() AS secondary_categories, JSON_ARRAY('第一次世界大战', '多人对战', '载具') AS tags,
    'battlefield-1' AS cover,
    '以第一次世界大战为背景的第一人称射击游戏。单人战役讲述多段战争故事，多人模式结合步兵、坦克和飞机等载具作战。' AS description,
    '战争故事跨越欧洲和中东等战场，多人模式中的行动模式将多张地图串成连续攻防战。' AS background,
    '一战背景大型多人战场 FPS，含空中狗斗与重型装甲战术' AS seed_description

  UNION ALL SELECT 'GAME-DS3', 'DARK SOULS III（ダークソウルIII）', 'Dark Souls III',
    JSON_ARRAY('黑魂3', 'DS3'), 'Bandai Namco Entertainment / FromSoftware', 'FromSoftware',
    '2016-03-24', JSON_ARRAY('Windows', 'PlayStation', 'Xbox'),
    JSON_ARRAY(), JSON_ARRAY('魂系', '黑暗奇幻', '高难度'),
    'dark-souls-3',
    '在洛斯里克探索衰败王国的动作角色扮演游戏。玩家可搭配武器、法术和战技，挑战首领并寻找失散的薪王。',
    '初火逐渐熄灭，曾经传火的薪王拒绝归位。被唤醒的无火余灰由此踏上旅程。',
    '高难度硬核动作游戏，强调武器质变、属性补正与盾反机制'

  UNION ALL SELECT 'CAT-WA2', 'WHITE ALBUM2', 'WHITE ALBUM2',
    JSON_ARRAY('WA2'), 'AQUAPLUS', 'Leaf',
    '2010-03-26', JSON_ARRAY('Windows', 'PlayStation'),
    JSON_ARRAY(), JSON_ARRAY('恋爱', '校园', '音乐'),
    'white-album-2',
    '以校园音乐演出为起点的恋爱视觉小说。故事分为序章和终章，描绘三人在毕业前后的选择与关系变化。',
    '为登上学园祭舞台，轻音乐同好会的北原春希找来主唱小木曾雪菜和钢琴手冬马和纱。排练让三人逐渐走近。',
    '围绕校园乐队展开的剧情视觉小说。'

  UNION ALL SELECT 'CAT-CLANNAD', 'CLANNAD', 'CLANNAD',
    JSON_ARRAY(), 'VISUAL ARTS', 'Key',
    '2004-04-28', JSON_ARRAY('Windows', 'Nintendo Switch', 'PlayStation', 'Xbox', 'Android'),
    JSON_ARRAY(), JSON_ARRAY('校园', '家庭', '恋爱'),
    'clannad',
    '围绕校园生活与家庭关系展开的视觉小说，玩家的选择会通往不同的角色路线，后日谈继续讲述毕业后的生活。',
    '对学校生活缺乏期待的冈崎朋也在坡道上遇见古河渚，并开始帮助她重建演剧部。',
    '以校园、日常和家庭关系为主题的视觉小说。'

  UNION ALL SELECT 'CAT-MAHOYO', '魔法使いの夜', 'WITCH ON THE HOLY NIGHT',
    JSON_ARRAY('魔夜', 'Mahoyo'), 'TYPE-MOON', 'TYPE-MOON',
    '2012-04-12', JSON_ARRAY('Windows', 'Nintendo Switch', 'PlayStation', 'Android', 'iOS'),
    JSON_ARRAY(), JSON_ARRAY('魔术', '都市奇幻', '青春'),
    'witch-on-the-holy-night',
    '以 1980 年代末为舞台的视觉小说，讲述苍崎青子、久远寺有珠与静希草十郎因魔术世界而交会的故事。',
    '青子和有珠住在山丘上的洋馆，来自乡间的少年草十郎目睹了本应隐于日常的魔术，三人的生活随之改变。',
    '描写魔术师日常与奇幻事件的视觉小说。'

  UNION ALL SELECT 'CAT-AOKANA', '蒼の彼方のフォーリズム', 'Aokana - Four Rhythms Across the Blue',
    JSON_ARRAY('Aokana'), 'sprite', 'sprite',
    '2014-11-28', JSON_ARRAY('Windows', 'Nintendo Switch', 'PlayStation'),
    JSON_ARRAY(), JSON_ARRAY('校园', '竞技', '恋爱'),
    'aokana',
    '围绕空中竞技 Flying Circus 展开的校园视觉小说，讲述选手训练、比赛和同伴关系。',
    '反重力鞋让飞行成为日常。转学生仓科明日香接触 Flying Circus，曾离开赛场的日向晶也也重新参与这项运动。',
    '围绕空中竞技与校园生活展开。'

  UNION ALL SELECT 'CAT-SENREN', '千恋＊万花', 'Senren＊Banka',
    JSON_ARRAY('千恋万花'), 'Yuzusoft', 'Yuzusoft',
    '2016-07-29', JSON_ARRAY('Windows', 'Nintendo Switch'),
    JSON_ARRAY(), JSON_ARRAY('恋爱', '和风', '奇幻'),
    'senren-banka',
    '以山间温泉小镇为舞台的恋爱视觉小说，故事围绕神刀、婚约和延续多年的诅咒展开。',
    '有地将臣回到外祖父所在的穗织，意外折断传说中的神刀丛雨丸，并由此与当地巫女及神刀的守护者相识。',
    '以和风小镇与传说为背景的视觉小说。'

  UNION ALL SELECT 'CAT-SUMMER', 'Summer Pockets', 'Summer Pockets',
    JSON_ARRAY(), 'VISUAL ARTS', 'Key',
    '2018-06-29', JSON_ARRAY('Windows', 'Nintendo Switch', 'Android', 'iOS'),
    JSON_ARRAY(), JSON_ARRAY('夏日', '海岛', '青春'),
    'summer-pockets',
    '描写岛上暑假与记忆的视觉小说。玩家通过在岛上行动和选择，与不同角色相遇并进入各自的故事。',
    '鹰原羽依里为整理祖母遗物来到鸟白岛，在远离城市的暑假里认识了岛上的少年少女。',
    '以海岛暑假为背景的剧情游戏。'

  UNION ALL SELECT 'CAT-STELLA', '終のステラ', 'Stella of The End',
    JSON_ARRAY('终之斯特拉'), 'VISUAL ARTS', 'Key',
    '2022-09-30', JSON_ARRAY('Windows', 'Nintendo Switch', 'Android', 'iOS'),
    JSON_ARRAY(), JSON_ARRAY('科幻', '末世', '公路故事'),
    'stella-of-the-end',
    '以人类文明衰落后的地球为舞台的科幻视觉小说，讲述运输员与一名人形机器人的旅程。',
    '在人类生活空间被机器挤压的世界里，运输员裘德受托护送机器人少女菲莉娅，两人横穿危险地带。',
    '以科幻旅程为主题的视觉小说。'

  UNION ALL SELECT 'CAT-GRIS', 'GRIS', 'GRIS',
    JSON_ARRAY(), 'Devolver Digital', 'Nomada Studio',
    '2018-12-13', JSON_ARRAY('Windows', 'macOS', 'Nintendo Switch', 'PlayStation', 'Xbox', 'Android', 'iOS'),
    JSON_ARRAY('PUZ / 解谜'), JSON_ARRAY('平台跳跃', '解谜', '手绘'),
    'gris',
    '以色彩和音乐叙事的平台冒险。格莉斯的裙装会随着旅程获得新能力，帮助她跨越障碍并解开环境谜题。',
    '格莉斯经历痛苦后迷失于自己的世界。随着她重新认识自己的感受，眼前的世界逐渐恢复色彩。',
    '以平台跳跃和场景解谜推动探索的冒险游戏。'

  UNION ALL SELECT 'CAT-HOLLOW', 'Hollow Knight', 'Hollow Knight',
    JSON_ARRAY(), 'Team Cherry', 'Team Cherry',
    '2017-02-24', JSON_ARRAY('Windows', 'macOS', 'Linux', 'Nintendo Switch', 'PlayStation', 'Xbox'),
    JSON_ARRAY('ADV / 冒险'), JSON_ARRAY('类银河战士恶魔城', '探索', '手绘'),
    'hollow-knight',
    '在相互连通的地下区域探索、战斗和收集能力的 2D 动作冒险。护符提供不同的能力组合，新能力可打开此前无法到达的路线。',
    '衰败小镇德特茅斯下方埋藏着虫类王国圣巢。玩家前往遗迹，追寻王国衰落和感染蔓延的原因。',
    '围绕地下王国展开的探索与动作游戏。'

  UNION ALL SELECT 'CAT-STARDEW', 'Stardew Valley', 'Stardew Valley',
    JSON_ARRAY(), 'ConcernedApe', 'ConcernedApe',
    '2016-02-26', JSON_ARRAY('Windows', 'macOS', 'Linux', 'Nintendo Switch', 'PlayStation', 'Xbox', 'Android', 'iOS'),
    JSON_ARRAY('SIM / 模拟', 'RPG / 角色扮演'), JSON_ARRAY('种植', '钓鱼', '农场经营'),
    'stardew-valley',
    '经营一座老农场，在不同季节种植作物、照料动物、钓鱼和采矿，并与鹈鹕镇的居民建立关系。',
    '玩家继承祖父在星露谷留下的农场，从旧工具和一笔启动资金开始，在小镇过上新的生活。',
    '经营农场并探索社区生活。'

  UNION ALL SELECT 'CAT-ACE', '逆転裁判123 成歩堂セレクション', 'Phoenix Wright: Ace Attorney Trilogy',
    JSON_ARRAY('成步堂精选集'), 'Capcom', 'Capcom',
    '2014-04-17', JSON_ARRAY('Windows', 'Nintendo Switch', 'PlayStation', 'Xbox', 'Android', 'iOS', '其他平台'),
    JSON_ARRAY('PUZ / 解谜'), JSON_ARRAY('法庭', '推理', '文字冒险'),
    'ace-attorney-trilogy',
    '收录《逆转裁判》前三部共 14 个案件。玩家在庭外调查取证，再在法庭交叉询问证人，用证物指出证词矛盾。',
    '新人辩护律师成步堂龙一在同伴协助下为委托人辩护，并逐步揭开几桩案件背后的关联。',
    '收集证物并在法庭上揭示证词矛盾。'

  UNION ALL SELECT 'CAT-P5R', 'ペルソナ５ ザ・ロイヤル', 'Persona 5 Royal',
    JSON_ARRAY('P5R'), 'ATLUS', 'ATLUS（P-Studio）',
    '2019-10-31', JSON_ARRAY('Windows', 'Nintendo Switch', 'PlayStation', 'Xbox'),
    JSON_ARRAY(), JSON_ARRAY('回合制', '校园', '怪盗'),
    'persona-5-royal',
    '《女神异闻录5》的扩充版本，结合校园日程、同伴协作与回合制战斗，新增角色和第三学期剧情。',
    '转学到东京的主角与伙伴组成心之怪盗团，进入由认知塑造的异世界，改变扭曲欲望的主人。',
    '结合日常互动与回合制战斗的角色扮演游戏。'
) v ON v.code = g.game_code
-- 旧基础数据接口可能绕过目录版本号，只替换空值和原始种子值。
SET g.cover_url = CASE
      WHEN NULLIF(TRIM(g.cover_url), '') IS NULL OR g.cover_url IN (
        '/uploads/game-bf1.jpg', '/uploads/game-ds3.jpg',
        '/catalog/cover-1.jpg', '/catalog/cover-2.jpg', '/catalog/cover-3.jpg',
        '/catalog/cover-4.jpg', '/catalog/cover-5.jpg', '/catalog/cover-6.jpg'
      ) THEN CONCAT('/catalog/game-covers/', v.cover, '.webp')
      ELSE g.cover_url
    END,
    g.description = CASE
      WHEN NULLIF(TRIM(g.description), '') IS NULL OR g.description = v.seed_description THEN v.description
      ELSE g.description
    END,
    c.original_name = v.original_name,
    c.english_name = v.english_name,
    c.aliases = v.aliases,
    c.company = v.company,
    c.developer = v.developer,
    c.background = v.background,
    c.secondary_categories = v.secondary_categories,
    c.tags = v.tags,
    c.secondary_platforms = CASE
      WHEN JSON_CONTAINS(v.platforms, JSON_QUOTE(g.platform))
        THEN JSON_REMOVE(v.platforms, JSON_UNQUOTE(JSON_SEARCH(v.platforms, 'one', g.platform)))
      ELSE v.platforms
    END,
    c.release_date = v.release_date,
    c.version = c.version + 1
WHERE @apply_verified_catalog AND c.version = 1 AND c.deleted_at IS NULL;
COMMIT;
