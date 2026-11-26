package com.gamekb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("game_guide")
public class GameGuide implements Serializable {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 攻略唯一编号 (类比WMS唯一单据号, 如 GD-20261117-0001) */
    private String guideCode;

    /** 所属游戏ID */
    private Long gameId;

    /** 投稿人ID */
    private Long authorId;

    /** 攻略标题 */
    private String title;

    /** 攻略分类标签 */
    private String categoryTag;

    /** 攻略摘要 */
    private String summary;

    /** Markdown格式攻略正文 */
    private String contentMd;

    /** 浏览量 */
    private Integer viewCount;

    /** 入库审核状态: 0-待审核, 1-已上架(入库), 2-已驳回 */
    private Integer auditStatus;

    /** 乐观锁版本号 */
    private Integer version;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}