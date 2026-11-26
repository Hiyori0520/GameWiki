package com.gamekb;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gamekb.common.BizException;
import com.gamekb.common.ErrorCode;
import com.gamekb.common.GlobalExceptionHandler;
import com.gamekb.common.PageQuery;
import com.gamekb.common.PageResult;
import com.gamekb.common.Result;
import com.gamekb.entity.GameGuide;
import com.gamekb.service.GameGuideService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 后端基础设施的冒烟测试。
 * 运行前需保证 MySQL(3306) 与 Redis(6379) 已启动，且已执行 database/00_init.sql。
 *
 * 运行： mvn -f backend/pom.xml test
 */
@SpringBootTest
class FoundationTest {

    @Resource
    private GameGuideService gameGuideService;

    @Test
    @DisplayName("分页拦截器生效：只取第 1 页 2 条，total 仍是全表条数")
    void paginationInterceptorWorks() {
        Page<GameGuide> page = new Page<>(1, 2);
        Page<GameGuide> result = gameGuideService.page(
                page, new LambdaQueryWrapper<GameGuide>().orderByAsc(GameGuide::getId));

        assertEquals(3, result.getTotal(), "种子数据共 3 篇攻略");
        assertEquals(2, result.getRecords().size(), "每页 2 条");
        assertEquals(2, result.getPages(), "3 条数据按每页 2 条应分成 2 页");
    }

    @Test
    @DisplayName("PageResult 与 MyBatis-Plus Page 的字段映射正确")
    void pageResultMappingWorks() {
        Page<GameGuide> page = new Page<>(2, 2);
        Page<GameGuide> result = gameGuideService.page(
                page, new LambdaQueryWrapper<GameGuide>().orderByAsc(GameGuide::getId));

        PageResult<GameGuide> mapped = PageResult.of(result);

        assertEquals(3, mapped.getTotal());
        assertEquals(1, mapped.getRecords().size(), "第 2 页只剩 1 条");
        assertEquals(2, mapped.getPage());
        assertEquals(2, mapped.getSize());
    }

    @Test
    @DisplayName("PageQuery 边界保护：page 归一到 1，size 上限 100")
    void pageQueryClampsInput() {
        PageQuery query = new PageQuery();
        query.setPage(0);
        query.setSize(9999);

        Page<Object> page = query.toPage();

        assertEquals(1, page.getCurrent());
        assertEquals(100, page.getSize());
    }

    @Test
    @DisplayName("业务异常被转换成对应业务码，而不是 500")
    void bizExceptionMapsToCode() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        Result<Void> forbidden = handler.handleBiz(new BizException(ErrorCode.FORBIDDEN));
        assertEquals(403, forbidden.getCode());
        assertEquals("无访问权限", forbidden.getMessage());

        Result<Void> custom = handler.handleBiz(new BizException("该用户名已被注册"));
        assertEquals(400, custom.getCode());
        assertTrue(custom.getMessage().contains("已被注册"));
    }
}
