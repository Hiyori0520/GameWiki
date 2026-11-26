package com.gamekb.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gamekb.auth.RequireRole;
import com.gamekb.common.Result;
import com.gamekb.entity.GameInfo;
import com.gamekb.entity.GameItemStats;
import com.gamekb.mapper.GameInfoMapper;
import com.gamekb.mapper.GameItemStatsMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "基础数据管理", description = "游戏分类字典与武器装备数值面板管理接口")
@RestController
@RequestMapping("/api/basic")
public class BasicDataController {

    @Resource
    private GameInfoMapper gameInfoMapper;
    @Resource
    private com.gamekb.service.CatalogGameService catalogGames;

    @Resource
    private GameItemStatsMapper gameItemStatsMapper;

    // ================== 1. 游戏分类字典管理 ==================

    @Operation(summary = "查询所有游戏分类")
    @GetMapping("/games")
    public Result<List<GameInfo>> listGames(@RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<GameInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.notInSql(GameInfo::getId, "SELECT game_id FROM catalog_game WHERE deleted_at IS NOT NULL");
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(filter -> filter.like(GameInfo::getGameName, keyword).or().like(GameInfo::getGenre, keyword));
        }
        return Result.success(gameInfoMapper.selectList(wrapper));
    }

    @Operation(summary = "新增或修改游戏分类")
    @RequireRole("ADMIN")
    @PostMapping("/games/save")
    public Result<Boolean> saveGame(@RequestBody GameInfo gameInfo) {
        if (gameInfo.getId() == null) {
            return Result.success(gameInfoMapper.insert(gameInfo) > 0);
        } else {
            return Result.success(gameInfoMapper.updateById(gameInfo) > 0);
        }
    }

    @Operation(summary = "删除游戏分类")
    @RequireRole("ADMIN")
    @DeleteMapping("/games/{id}")
    public Result<Boolean> deleteGame(@PathVariable("id") Long id) {
        catalogGames.change(id, "delete", List.of());
        return Result.success(true);
    }

    // ================== 2. 武器装备数值面板管理 ==================

    @Operation(summary = "查询武器装备面板列表")
    @GetMapping("/items")
    public Result<List<GameItemStats>> listItems(@RequestParam(required = false) Long gameId,
                                                 @RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<GameItemStats> wrapper = new LambdaQueryWrapper<>();
        if (gameId != null) {
            wrapper.eq(GameItemStats::getGameId, gameId);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(GameItemStats::getItemName, keyword).or().like(GameItemStats::getItemType, keyword);
        }
        return Result.success(gameItemStatsMapper.selectList(wrapper));
    }

    @Operation(summary = "新增或修改武器装备数值")
    @RequireRole("ADMIN")
    @PostMapping("/items/save")
    public Result<Boolean> saveItem(@RequestBody GameItemStats item) {
        if (item.getId() == null) {
            return Result.success(gameItemStatsMapper.insert(item) > 0);
        } else {
            return Result.success(gameItemStatsMapper.updateById(item) > 0);
        }
    }

    @Operation(summary = "删除武器装备数值")
    @RequireRole("ADMIN")
    @DeleteMapping("/items/{id}")
    public Result<Boolean> deleteItem(@PathVariable("id") Long id) {
        return Result.success(gameItemStatsMapper.deleteById(id) > 0);
    }
}