package com.gamekb.service;

import com.gamekb.common.BizException;
import com.gamekb.common.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.gamekb.service.CatalogDatabase.*;

@Service
public class CatalogGameService {
    private static final String JOIN = " FROM game_info g LEFT JOIN catalog_game x ON x.game_id=g.id";
    private static final String SELECT = "SELECT g.*,x.original_name,x.english_name,x.aliases,x.company,x.developer,x.background,x.secondary_categories,x.tags,x.secondary_platforms,x.release_date,x.rating,x.votes,x.status,x.version,(SELECT COUNT(*) FROM game_guide d WHERE d.game_id=g.id AND d.audit_status=1) guide_count";
    private static final String STATUS = "COALESCE(x.status,'PUBLISHED')";
    private static final Map<String, String> SORTS = Map.of("name", "g.game_name", "createdAt", "g.create_time", "rating", "x.rating", "releaseDate", "x.release_date", "guides", "guide_count");
    private static final Map<String, String> NEXT = Map.of("submit-review", "PENDING_REVIEW", "publish", "PUBLISHED", "off-shelf", "OFF_SHELF", "reject", "DRAFT");
    private final CatalogDatabase db;

    public CatalogGameService(CatalogDatabase db) { this.db = db; }

    private Map<String, Object> record(Map<String, Object> row) {
        return map("id", row.get("id"), "name", row.get("game_name"), "cover", text(row.get("cover_url")),
                "description", text(row.get("description")), "originalName", text(row.get("original_name")),
                "englishName", text(row.get("english_name")), "aliases", db.strings(row.get("aliases")),
                "company", text(row.get("company")), "developer", text(row.get("developer")),
                "background", text(row.get("background")), "categories", combined(row.get("genre"), db.strings(row.get("secondary_categories"))),
                "tags", db.strings(row.get("tags")), "platforms", combined(row.get("platform"), db.strings(row.get("secondary_platforms"))),
                "releaseDate", text(iso(row.get("release_date"))), "rating", row.get("rating"),
                "votes", number(row.get("votes"), 0), "guideCount", row.get("guide_count"), "status", row.getOrDefault("status", "") == null ? "PUBLISHED" : row.get("status"),
                "createdAt", iso(row.get("create_time")), "version", number(row.get("version"), 1));
    }

    private Filter filter(Map<String, ?> params, boolean published) {
        var filter = new Filter("x.deleted_at IS NULL");
        String keyword = text(params, "keyword");
        String category = text(params, "category");
        String platform = text(params, "platform");
        String status = published ? "PUBLISHED" : text(params, "status");
        if (!keyword.isEmpty()) {
            String like = "%" + keyword + "%";
            filter.add("(g.game_name LIKE ? OR g.description LIKE ? OR x.original_name LIKE ? OR x.english_name LIKE ? OR CAST(x.tags AS CHAR) LIKE ? OR CAST(x.aliases AS CHAR) LIKE ?)", like, like, like, like, like, like);
        }
        if (!category.isEmpty()) filter.add("(g.genre=? OR JSON_CONTAINS(x.secondary_categories,JSON_QUOTE(?)))", category, category);
        if (!platform.isEmpty()) filter.add("(g.platform=? OR JSON_CONTAINS(x.secondary_platforms,JSON_QUOTE(?)))", platform, platform);
        if (!text(params, "tag").isEmpty()) filter.add("JSON_CONTAINS(x.tags,JSON_QUOTE(?))", text(params, "tag"));
        if (!text(params, "company").isEmpty()) filter.add("x.company=?", text(params, "company"));
        if (!status.isEmpty()) filter.add(STATUS + "=?", status);
        return filter;
    }

    public Map<String, Object> page(Map<String, ?> params, boolean published) {
        int page = pageNumber(params), size = pageSize(params);
        Filter filter = filter(params, published);
        long total = db.count("SELECT COUNT(*)" + JOIN + filter.where(), filter.args());
        return CatalogDatabase.page(rows(params, filter, size, (page - 1) * size), total, page, size);
    }

    public List<Map<String, Object>> export(Map<String, ?> params, List<Long> ids) {
        Filter filter = filter(params, false);
        if (!ids.isEmpty()) filter.add("g.id IN (" + String.join(",", java.util.Collections.nCopies(ids.size(), "?")) + ")", ids.toArray());
        return rows(params, filter, 10000, 0);
    }

    private List<Map<String, Object>> rows(Map<String, ?> params, Filter filter, int limit, int offset) {
        String sort = SORTS.getOrDefault(text(params, "sortBy"), "g.create_time");
        String direction = text(params, "sortOrder").equals("asc") ? " ASC" : " DESC";
        String where = filter.where();
        return db.jdbc.queryForList(SELECT + JOIN + where + " ORDER BY " + sort + direction + ",g.id DESC LIMIT ? OFFSET ?",
                filter.with(limit, offset)).stream().map(this::record).toList();
    }

    public Map<String, Object> get(long id, boolean published) {
        String condition = published ? " AND " + STATUS + "='PUBLISHED'" : "";
        return record(db.one(SELECT + JOIN + " WHERE x.deleted_at IS NULL AND g.id=?" + condition, id));
    }

    public Map<String, Object> options() {
        List<String> tags = db.jdbc.queryForList("SELECT DISTINCT t.tag FROM catalog_game x JOIN JSON_TABLE(x.tags,'$[*]' COLUMNS(tag VARCHAR(50) PATH '$')) t WHERE x.deleted_at IS NULL ORDER BY t.tag", String.class);
        return Map.of("categories", db.jdbc.queryForList("SELECT name FROM catalog_category ORDER BY sort_order,name", String.class),
                "platforms", db.jdbc.queryForList("SELECT name FROM catalog_platform ORDER BY sort_order,name", String.class),
                "companies", db.jdbc.queryForList("SELECT DISTINCT company FROM catalog_game WHERE company<>'' AND deleted_at IS NULL ORDER BY company", String.class),
                "tags", tags);
    }

    public Map<String, Object> stats() {
        String counts = "SELECT COUNT(*) total, COUNT(CASE WHEN " + STATUS + "='PUBLISHED' THEN 1 END) published," +
                " COUNT(CASE WHEN x.status='PENDING_REVIEW' THEN 1 END) pending, COUNT(CASE WHEN x.status='OFF_SHELF' THEN 1 END) offShelf";
        var all = db.one(counts + JOIN + " WHERE x.deleted_at IS NULL");
        all.put("recent", db.one(counts + JOIN + " WHERE x.deleted_at IS NULL AND g.create_time>=CURRENT_DATE-INTERVAL 6 DAY"));
        return all;
    }

    @Transactional
    public Map<String, Object> save(Long id, Map<String, Object> input) {
        String name = field(input, "name", 100, true);
        String description = field(input, "description", 500, false);
        String cover = field(input, "cover", 255, false);
        List<String> categories = list(input.get("categories"), 8, 50);
        List<String> platforms = list(input.get("platforms"), 9, 50);
        List<String> tags = list(input.get("tags"), 8, 50);
        if (categories.isEmpty() || platforms.isEmpty()) invalid("请选择游戏分类和平台");
        for (String category : categories)
            if (db.count("SELECT COUNT(*) FROM catalog_category WHERE name=?", category) == 0) invalid("游戏分类不存在");
        for (String platform : platforms)
            if (db.count("SELECT COUNT(*) FROM catalog_platform WHERE name=?", platform) == 0) invalid("游戏平台不存在");
        if (db.count("SELECT COUNT(*)" + JOIN + " WHERE x.deleted_at IS NULL AND g.game_name=? AND g.id<>?", name, id == null ? 0 : id) > 0)
            conflict("同名游戏已经存在");
        boolean creating = id == null;
        if (creating) {
            id = db.insert("INSERT INTO game_info(game_code,game_name,genre,platform,cover_url,description) VALUES(?,?,?,?,?,?)",
                    "G-" + UUID.randomUUID().toString().replace("-", "").substring(0, 28), name, categories.get(0), platforms.get(0), cover, description);
            db.jdbc.update("INSERT INTO catalog_game(game_id,status) VALUES(?,'DRAFT')", id);
        } else {
            lock(id);
            long expected = number(input.get("version"), 0);
            if (expected > 0 && expected != db.count("SELECT version FROM catalog_game WHERE game_id=?", id)) conflict("游戏已更新，请重新载入");
            db.jdbc.update("UPDATE game_info SET game_name=?,genre=?,platform=?,cover_url=?,description=? WHERE id=?",
                    name, categories.get(0), platforms.get(0), cover, description, id);
        }
        String date = text(input, "releaseDate");
        Date release = null;
        if (!date.isEmpty()) {
            try { release = Date.valueOf(date); }
            catch (IllegalArgumentException e) { invalid("发布日期格式有误"); }
        }
        db.jdbc.update("""
            UPDATE catalog_game SET original_name=?,english_name=?,aliases=?,company=?,developer=?,background=?,
                secondary_categories=?,tags=?,secondary_platforms=?,release_date=?,version=version+? WHERE game_id=?
            """, field(input, "originalName", 160, false), field(input, "englishName", 160, false),
                db.json(list(input.get("aliases"), 8, 80)), field(input, "company", 120, false),
                field(input, "developer", 120, false), field(input, "background", 255, false),
                db.json(categories.subList(1, categories.size())), db.json(tags), db.json(platforms.subList(1, platforms.size())),
                release, creating ? 0 : 1, id);
        db.log("游戏管理", creating ? "添加" : "编辑", name);
        return get(id, false);
    }

    private Map<String, Object> lock(long id) {
        db.one("SELECT id FROM game_info WHERE id=? FOR UPDATE", id);
        db.jdbc.update("INSERT IGNORE INTO catalog_game(game_id) VALUES(?)", id);
        return get(id, false);
    }

    @Transactional
    public void change(long id, String action, List<String> values) {
        Map<String, Object> game = lock(id);
        String status = text(game, "status");
        if (action.equals("delete")) {
            if (db.count("SELECT COUNT(*) FROM game_guide WHERE game_id=?", id) > 0) conflict("此游戏仍关联攻略，解除关联后才能删除。");
            db.jdbc.update("UPDATE catalog_game SET deleted_at=NOW(),version=version+1 WHERE game_id=?", id);
        } else if (action.equals("add-tags") || action.equals("set-categories")) {
            if (action.equals("add-tags")) {
                var updated = new LinkedHashSet<>(list(game.get("tags"), 8, 50));
                updated.addAll(values);
                game.put("tags", new ArrayList<>(updated));
            } else game.put("categories", values);
            save(id, game);
            return;
        } else {
            boolean allowed = switch (action) {
                case "submit-review" -> status.equals("DRAFT");
                case "publish" -> status.equals("PENDING_REVIEW") || status.equals("OFF_SHELF");
                case "off-shelf" -> status.equals("PUBLISHED");
                case "reject" -> status.equals("PENDING_REVIEW");
                default -> throw new BizException(ErrorCode.PARAM_ERROR, "未知的游戏操作");
            };
            if (!allowed) conflict("游戏当前状态不支持此操作");
            db.jdbc.update("UPDATE catalog_game SET status=?,version=version+1 WHERE game_id=?", NEXT.get(action), id);
        }
        db.log("游戏管理", action, game.get("name"));
    }
}
