package com.gamekb.controller;

import com.gamekb.auth.RequireRole;
import com.gamekb.common.Result;
import com.gamekb.entity.GameGuide;
import com.gamekb.service.GameGuideService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "游戏攻略知识库管理", description = "包含攻略投稿、审核入库、缓存查询与检索接口")
@RestController
@RequestMapping("/api/guide")
public class GameGuideController {

    @Resource
    private GameGuideService gameGuideService;

    @Operation(summary = "玩家投稿新攻略（自动生成唯一编号）")
    @PostMapping("/submit")
    public Result<GameGuide> submit(@RequestBody GameGuide guide) {
        return Result.success("投稿成功，等待审核入库", gameGuideService.submitGuide(guide));
    }

    @Operation(summary = "查询已发布攻略详情")
    @GetMapping("/{id}")
    public Result<GameGuide> detail(@PathVariable Long id) {
        GameGuide guide = gameGuideService.getPublishedGuide(id);
        if (guide == null) {
            return Result.error("攻略不存在或尚未审核上架");
        }
        return Result.success(guide);
    }

    @Operation(summary = "修改攻略或审核状态变更（触发缓存一致性淘汰）")
    @RequireRole("ADMIN")
    @PutMapping("/update")
    public Result<Boolean> update(@RequestBody GameGuide guide) {
        return Result.success("更新成功并已同步清理缓存", gameGuideService.updateGuideWithConsistency(guide));
    }

    @Operation(summary = "检索已上架攻略列表")
    @GetMapping("/search")
    public Result<List<GameGuide>> search(
            @RequestParam(required = false) Long gameId,
            @RequestParam(required = false) String keyword) {
        return Result.success(gameGuideService.searchPublishedGuides(gameId, keyword));
    }
    @Operation(summary = "查询全部攻略列表（含待审核，供后台管理使用）")
    @RequireRole("ADMIN")
    @GetMapping("/all")
    public Result<List<GameGuide>> listAllGuides(@RequestParam(required = false) Integer auditStatus,
                                                 @RequestParam(required = false) String keyword) {
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<GameGuide> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        if (auditStatus != null) {
            wrapper.eq(GameGuide::getAuditStatus, auditStatus);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(GameGuide::getTitle, keyword).or().like(GameGuide::getGuideCode, keyword);
        }
        wrapper.orderByDesc(GameGuide::getCreateTime);
        return Result.success(gameGuideService.list(wrapper));
    }

    @Operation(summary = "删除攻略（同步清理Redis缓存）")
    @RequireRole("ADMIN")
    @DeleteMapping("/{id}")
    public Result<Boolean> deleteGuide(@PathVariable("id") Long id) {
        return Result.success(gameGuideService.deleteGuide(id));
    }

}