package com.gamekb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("game_info")
public class GameInfo {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String gameName;
    private String genre;
    private String platform;
    private String description;
    private LocalDateTime createTime;
}