package com.gamekb.controller;

import com.gamekb.auth.UserContext;
import com.gamekb.common.BizException;
import com.gamekb.common.ErrorCode;
import com.gamekb.common.Result;
import com.gamekb.service.CatalogDatabase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 对话历史：会话列表、会话消息、删除会话。
 * 数据来自 ai_chat_log（写入由 AiAgentService 负责），这里只读。
 */
@Tag(name = "AI 对话历史", description = "当前用户的会话列表与会话消息")
@RestController
@RequestMapping("/api/ai")
public class AiHistoryController {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Resource
    private CatalogDatabase db;

    @Operation(summary = "当前用户的会话列表（按最后活动时间倒序）")
    @GetMapping("/sessions")
    public Result<List<Map<String, Object>>> sessions() {
        long userId = UserContext.requireUserId();

        List<Map<String, Object>> rows = db.jdbc.queryForList("""
                SELECT s.session_id, s.started_at, s.last_at, s.msg_count, t.content AS title
                FROM (
                    SELECT session_id, MIN(create_time) AS started_at,
                           MAX(create_time) AS last_at, COUNT(*) AS msg_count
                    FROM ai_chat_log
                    WHERE user_id = ?
                    GROUP BY session_id
                ) s
                LEFT JOIN (
                    SELECT session_id, content,
                           ROW_NUMBER() OVER (PARTITION BY session_id ORDER BY id) AS rn
                    FROM ai_chat_log
                    WHERE user_id = ? AND role = 'user'
                ) t ON t.session_id = s.session_id AND t.rn = 1
                ORDER BY s.last_at DESC
                LIMIT 50
                """, userId, userId);

        List<Map<String, Object>> list = rows.stream().map(row -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("sessionId", CatalogDatabase.text(row, "session_id"));
            item.put("title", shortTitle(CatalogDatabase.text(row, "title")));
            item.put("startedAt", fmt(row.get("started_at")));
            item.put("lastAt", fmt(row.get("last_at")));
            item.put("messageCount", ((Number) row.get("msg_count")).intValue());
            return item;
        }).toList();

        return Result.success(list);
    }

    @Operation(summary = "某个会话的全部消息")
    @GetMapping("/sessions/{sessionId}")
    public Result<List<Map<String, Object>>> messages(@PathVariable String sessionId) {
        long userId = UserContext.requireUserId();

        List<Map<String, Object>> rows = db.jdbc.queryForList("""
                SELECT id, role, content, create_time
                FROM ai_chat_log
                WHERE user_id = ? AND session_id = ?
                ORDER BY id ASC
                LIMIT 200
                """, userId, sessionId);

        if (rows.isEmpty()) {
            throw new BizException(ErrorCode.NOT_FOUND, "会话不存在或已删除");
        }

        return Result.success(rows.stream().map(row -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", ((Number) row.get("id")).longValue());
            // 库里存的是 user / assistant，前端统一用 user / ai
            item.put("role", "assistant".equals(row.get("role")) ? "ai" : "user");
            item.put("content", CatalogDatabase.text(row, "content"));
            item.put("createTime", fmt(row.get("create_time")));
            return item;
        }).toList());
    }

    @Operation(summary = "删除会话（连同该会话的所有消息）")
    @DeleteMapping("/sessions/{sessionId}")
    public Result<Integer> delete(@PathVariable String sessionId) {
        long userId = UserContext.requireUserId();
        int deleted = db.jdbc.update(
                "DELETE FROM ai_chat_log WHERE user_id = ? AND session_id = ?", userId, sessionId);
        if (deleted == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "会话不存在或已删除");
        }
        return Result.success("已删除该会话", deleted);
    }

    private String shortTitle(String text) {
        if (text == null || text.isBlank()) {
            return "新对话";
        }
        String t = text.replaceAll("\\s+", " ").trim();
        return t.length() <= 24 ? t : t.substring(0, 24) + "…";
    }

    private String fmt(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof LocalDateTime time) {
            return time.format(FMT);
        }
        if (value instanceof Timestamp ts) {
            return ts.toLocalDateTime().format(FMT);
        }
        return value.toString();
    }
}
