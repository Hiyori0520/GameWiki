package com.gamekb.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gamekb.auth.UserContext;
import com.gamekb.common.BizException;
import com.gamekb.common.ErrorCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Component;

import java.sql.Statement;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class CatalogDatabase {
    public final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public CatalogDatabase(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public Map<String, Object> one(String sql, Object... args) {
        return jdbc.queryForList(sql, args).stream().findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "记录不存在"));
    }

    public long count(String sql, Object... args) {
        return Objects.requireNonNull(jdbc.queryForObject(sql, Long.class, args));
    }

    public long insert(String sql, Object... args) {
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
            return statement;
        }, key);
        return Objects.requireNonNull(key.getKey()).longValue();
    }

    public void log(String module, String action, Object target) {
        jdbc.update("INSERT INTO admin_activity(actor,module,action,target) VALUES(?,?,?,?)",
                UserContext.get().getUsername(), module, action, target.toString());
    }

    public String json(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("无法保存 JSON 数据", e);
        }
    }

    public List<String> strings(Object value) {
        if (value == null) return List.of();
        try {
            return json.readValue(value.toString(), new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("数据库中的列表格式有误", e);
        }
    }

    public static String text(Object value) { return value == null ? "" : value.toString().trim(); }
    public static String text(Map<String, ?> values, String key) { return text(values.get(key)); }

    public static String field(Map<String, ?> values, String key, int length, boolean required) {
        String value = text(values, key);
        if ((required && value.isEmpty()) || value.length() > length) invalid("字段格式有误：" + key);
        return value;
    }

    public static long number(Object value, long fallback) {
        String text = text(value);
        if (text.isEmpty()) return fallback;
        try { return Long.parseLong(text); }
        catch (NumberFormatException e) { throw new BizException(ErrorCode.PARAM_ERROR, "无效的数字"); }
    }

    public static List<String> list(Object value, int max, int maxLength) {
        if (value == null) return List.of();
        if (!(value instanceof List<?> rows)) throw new BizException(ErrorCode.PARAM_ERROR, "需要列表参数");
        var entries = rows.stream().map(CatalogDatabase::text).filter(v -> !v.isBlank()).distinct().toList();
        if (entries.size() > max || entries.stream().anyMatch(v -> v.length() > maxLength)) invalid("列表项超出长度限制");
        return entries;
    }

    public static List<String> combined(Object first, List<String> rest) {
        var list = new ArrayList<String>();
        if (!text(first).isEmpty()) list.add(text(first));
        list.addAll(rest);
        return list.stream().distinct().toList();
    }

    public static String iso(Object value) {
        if (value instanceof java.sql.Timestamp timestamp) return timestamp.toLocalDateTime().toString();
        if (value instanceof java.sql.Date date) return date.toLocalDate().toString();
        if (value instanceof TemporalAccessor) return value.toString();
        return value == null ? null : value.toString().replace(' ', 'T');
    }

    public static Map<String, Object> page(List<?> records, long total, int page, int size) {
        return Map.of("records", records, "total", total, "page", page, "pageSize", size);
    }

    public static int pageNumber(Map<String, ?> params) {
        return (int) Math.min(100000, Math.max(1, number(params.get("page"), 1)));
    }
    public static int pageSize(Map<String, ?> params) {
        return (int) Math.min(100, Math.max(1, number(params.get("pageSize"), 20)));
    }

    public static Map<String, Object> map(Object... pairs) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) row.put(pairs[i].toString(), pairs[i + 1]);
        return row;
    }

    public Object readJson(String value) {
        try { return json.readValue(value, Object.class); }
        catch (JsonProcessingException e) { throw new IllegalStateException("数据库中的 JSON 格式有误", e); }
    }

    public static class Filter {
        private final List<String> clauses = new ArrayList<>();
        private final List<Object> values = new ArrayList<>();
        public Filter(String clause) { add(clause); }
        public void add(String clause, Object... args) {
            clauses.add(clause);
            java.util.Collections.addAll(values, args);
        }
        public String where() { return " WHERE " + String.join(" AND ", clauses); }
        public Object[] args() { return values.toArray(); }
        public Object[] with(Object... extra) {
            var result = new ArrayList<>(values);
            java.util.Collections.addAll(result, extra);
            return result.toArray();
        }
    }

    public static void invalid(String message) { throw new BizException(ErrorCode.PARAM_ERROR, message); }
    public static void conflict(String message) { throw new BizException(409, message); }
}
