package com.gamekb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("game_item_stats")
public class GameItemStats {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long gameId;
    private String itemName;
    private String itemType;
    private String damageInfo;
    private String scalingOrFeature;
    private String obtainMethod;
    private LocalDateTime createTime;
}