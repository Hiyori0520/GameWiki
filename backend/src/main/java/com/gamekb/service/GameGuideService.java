package com.gamekb.service;

import cn.hutool.core.date.DateUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gamekb.entity.GameGuide;
import com.gamekb.auth.UserContext;
import com.gamekb.common.BizException;
import com.gamekb.mapper.GameGuideMapper;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class GameGuideService extends ServiceImpl<GameGuideMapper, GameGuide> {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    @Resource
    private CatalogGameService catalogGames;
    @Resource
    private CatalogDatabase db;

    private static final String GUIDE_CACHE_PREFIX = "kb:guide:detail:";
    private static final String GUIDE_SEQ_PREFIX = "kb:seq:guide:";

    /**
     * 1. 攻略投稿（类比 WMS 入库登记 + Redis INCR 生成唯一编号）
     */
    public GameGuide submitGuide(GameGuide guide) {
        if (guide.getGameId() == null) throw new BizException("请选择所属游戏");
        catalogGames.get(guide.getGameId(), true);
        guide.setId(null);
        guide.setAuthorId(UserContext.get().getId());
        String today = DateUtil.format(new Date(), "yyyyMMdd");
        String seqKey = GUIDE_SEQ_PREFIX + today;
        Long seq = redisTemplate.opsForValue().increment(seqKey, 1);
        redisTemplate.expire(seqKey, 2, TimeUnit.DAYS);

        // 生成全局唯一编号，例如：GD-20261117-0001
        String uniqueCode = String.format("GD-%s-%04d", today, seq);
        guide.setGuideCode(uniqueCode);
        guide.setAuditStatus(0); // 默认待审核入库
        guide.setViewCount(0);
        guide.setVersion(1);

        this.save(guide);
        return guide;
    }

    public GameGuide getPublishedGuide(Long id) {
        var query = new LambdaQueryWrapper<GameGuide>().eq(GameGuide::getId, id).eq(GameGuide::getAuditStatus, 1)
                .notInSql(GameGuide::getGameId, "SELECT game_id FROM catalog_game WHERE status<>'PUBLISHED' OR deleted_at IS NOT NULL");
        GameGuide guide = getOne(query);
        if (guide != null) {
            db.jdbc.update("UPDATE game_guide SET view_count=view_count+1 WHERE id=?", id);
            guide.setViewCount(guide.getViewCount() + 1);
        }
        return guide;
    }

    @Transactional
    public boolean deleteGuide(long id) {
        db.one("SELECT id FROM game_guide WHERE id=? FOR UPDATE", id);
        if (db.count("SELECT COUNT(*) FROM catalog_guide WHERE revision_of=?", id) > 0)
            CatalogDatabase.conflict("请先处理该攻略的新版本");
        db.jdbc.update("DELETE FROM game_favorite WHERE guide_id=?", id);
        db.jdbc.update("DELETE FROM guide_like WHERE guide_id=?", id);
        db.jdbc.update("DELETE FROM browse_history WHERE target_type='guide' AND target_id=?", id);
        return removeById(id);
    }

    /**
     * 3. 更新攻略或审核上下架（缓存一致性解决方案：先更新数据库，再删除缓存）
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean updateGuideWithConsistency(GameGuide guide) {
        boolean updated = this.updateById(guide);
        if (updated && guide.getId() != null) {
            // 删除 Redis 中的旧缓存，确保下次读取加载最新数据
            redisTemplate.delete(GUIDE_CACHE_PREFIX + guide.getId());
        }
        return updated;
    }

    /**
     * 4. 按关键词或游戏ID检索已上架攻略（后续也将作为 Spring AI 的检索工具）
     */
    public List<GameGuide> searchPublishedGuides(Long gameId, String keyword) {
        LambdaQueryWrapper<GameGuide> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(GameGuide::getAuditStatus, 1)
                .notInSql(GameGuide::getGameId, "SELECT game_id FROM catalog_game WHERE status<>'PUBLISHED' OR deleted_at IS NOT NULL");
        if (gameId != null) {
            wrapper.eq(GameGuide::getGameId, gameId);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(GameGuide::getTitle, keyword)
                    .or().like(GameGuide::getCategoryTag, keyword)
                    .or().like(GameGuide::getSummary, keyword));
        }
        wrapper.orderByDesc(GameGuide::getViewCount);
        return this.list(wrapper);
    }
}