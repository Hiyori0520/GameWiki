package com.gamekb.common;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 统一分页响应体，对应 docs/API契约.md 第二节：
 * <pre>
 * data: { records: [], total: 42, page: 1, size: 10 }
 * </pre>
 *
 * 用法（Service 层）：
 * <pre>
 * Page&lt;GameGuide&gt; page = query.toPage();
 * gameGuideService.page(page, wrapper);
 * return Result.success(PageResult.of(page));
 * </pre>
 */
@Data
public class PageResult<T> {

    private List<T> records = new ArrayList<>();
    private long total;
    private long page;
    private long size;

    public static <T> PageResult<T> of(IPage<T> source) {
        PageResult<T> result = new PageResult<>();
        result.setRecords(source.getRecords());
        result.setTotal(source.getTotal());
        result.setPage(source.getCurrent());
        result.setSize(source.getSize());
        return result;
    }

    /** 手动分页（例如先查全量再截取，不推荐但应急可用） */
    public static <T> PageResult<T> of(List<T> all, long page, long size) {
        PageResult<T> result = new PageResult<>();
        int from = (int) Math.min((page - 1) * size, all.size());
        int to = (int) Math.min(from + size, all.size());
        result.setRecords(all.subList(from, to));
        result.setTotal(all.size());
        result.setPage(page);
        result.setSize(size);
        return result;
    }
}
