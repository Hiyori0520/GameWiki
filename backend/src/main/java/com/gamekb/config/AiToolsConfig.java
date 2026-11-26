package com.gamekb.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gamekb.entity.GameGuide;
import com.gamekb.entity.GameItemStats;
import com.gamekb.mapper.GameItemStatsMapper;
import com.gamekb.service.GameGuideService;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@Configuration
public class AiToolsConfig {

    @Resource
    private GameGuideService gameGuideService;

    @Resource
    private GameItemStatsMapper gameItemStatsMapper;

    public record SearchRequest(String keyword) {}

    /**
     * 工具 1：供 Spring AI 自动调用，检索本地游戏攻略知识库
     */
    @Bean
    @Description("根据游戏名称、BOSS名称、武器或战术关键词，从本地知识库中检索已上架的游戏攻略正文")
    public Function<SearchRequest, String> searchGameGuideTool() {
        return request -> {
            List<GameGuide> guides = gameGuideService.searchPublishedGuides(null, request.keyword());
            if (guides.isEmpty()) {
                return "本地知识库暂未收录关于【" + request.keyword() + "】的攻略。";
            }
            return guides.stream()
                    .limit(3)
                    .map(g -> "【攻略编号：" + g.getGuideCode() + " | 标题：" + g.getTitle() + "】\n内容：" + g.getContentMd())
                    .collect(Collectors.joining("\n\n---\n\n"));
        };
    }

    /**
     * 工具 2：供 Spring AI 自动调用，查询武器/装备精确伤害与获取方式
     */
    @Bean
    @Description("根据武器或装备名称，查询游戏中的精确伤害面板、属性补正及解锁获取方式")
    public Function<SearchRequest, String> queryItemStatsTool() {
        return request -> {
            LambdaQueryWrapper<GameItemStats> wrapper = new LambdaQueryWrapper<>();
            wrapper.like(GameItemStats::getItemName, request.keyword());
            List<GameItemStats> items = gameItemStatsMapper.selectList(wrapper);
            if (items.isEmpty()) {
                return "装备库中未找到名为【" + request.keyword() + "】的数值记录。";
            }
            return items.stream()
                    .map(item -> String.format("装备名称：%s（类型：%s）\n面板/伤害：%s\n特性/补正：%s\n获取方式：%s",
                            item.getItemName(), item.getItemType(), item.getDamageInfo(),
                            item.getScalingOrFeature(), item.getObtainMethod()))
                    .collect(Collectors.joining("\n\n"));
        };
    }
}