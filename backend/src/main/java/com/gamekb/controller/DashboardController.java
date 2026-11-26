package com.gamekb.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gamekb.common.Result;
import com.gamekb.entity.GameGuide;
import com.gamekb.entity.GameInfo;
import com.gamekb.mapper.GameInfoMapper;
import com.gamekb.mapper.GameItemStatsMapper;
import com.gamekb.mapper.SysUserMapper;
import com.gamekb.service.GameGuideService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

@Tag(name = "工作台与查询统计", description = "首页数据概览、ECharts图表统计及Redis实时热度榜接口")
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    @Resource
    private GameGuideService gameGuideService;

    @Resource
    private GameInfoMapper gameInfoMapper;

    @Resource
    private GameItemStatsMapper gameItemStatsMapper;

    @Resource
    private SysUserMapper sysUserMapper;

    @Operation(summary = "获取工作台统计总览与图表数据")
    @GetMapping("/overview")
    public Result getOverview() {
        Map<String, Object> result = new HashMap<>();

        long totalGuides = gameGuideService.count();
        long pendingGuides = gameGuideService.count(new LambdaQueryWrapper<GameGuide>().eq(GameGuide::getAuditStatus, 0));
        long totalItems = gameItemStatsMapper.selectCount(null);
        long totalUsers = sysUserMapper.selectCount(null);

        result.put("totalGuides", totalGuides);
        result.put("pendingGuides", pendingGuides);
        result.put("totalItems", totalItems);
        result.put("totalUsers", totalUsers);

        List<GameInfo> games = gameInfoMapper.selectList(new LambdaQueryWrapper<GameInfo>()
                .notInSql(GameInfo::getId, "SELECT game_id FROM catalog_game WHERE deleted_at IS NOT NULL"));
        List gameDistribution = new ArrayList<>();
        for (GameInfo game : games) {
            long count = gameGuideService.count(new LambdaQueryWrapper<GameGuide>().eq(GameGuide::getGameId, game.getId()));
            Map<String, Object> item = new HashMap<>();
            item.put("name", game.getGameName());
            item.put("value", count);
            gameDistribution.add(item);
        }
        result.put("gameDistribution", gameDistribution);

        var hotList = gameGuideService.searchPublishedGuides(null, null).stream().limit(5)
                .map(guide -> Map.of("id", guide.getId(), "guideCode", guide.getGuideCode(),
                        "title", guide.getTitle(), "hotScore", guide.getViewCount())).toList();
        result.put("hotRankList", hotList);

        return Result.success(result);
    }
}