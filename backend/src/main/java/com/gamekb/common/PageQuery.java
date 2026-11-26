package com.gamekb.common;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;

/**
 * 分页查询参数基类。Controller 直接继承，即可获得统一的 page/size，
 * 且自动做边界保护，避免前端传 size=99999 把数据库拖死。
 *
 * <pre>
 * public class GuideQuery extends PageQuery {
 *     private String keyword;
 * }
 * </pre>
 */
@Data
public class PageQuery {

    /** 页码，从 1 开始 */
    private long page = 1;

    /** 每页条数，默认 10，上限 100 */
    private long size = 10;

    public <T> Page<T> toPage() {
        long safePage = Math.max(page, 1);
        long safeSize = Math.min(Math.max(size, 1), 100);
        return new Page<>(safePage, safeSize);
    }
}
