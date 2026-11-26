package com.gamekb.service;

import com.gamekb.auth.UserContext;
import com.gamekb.common.BizException;
import com.gamekb.common.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.gamekb.service.CatalogDatabase.*;

@Service
public class CatalogGuideService {
    private static final String JOIN = """
        FROM game_guide d JOIN game_info g ON g.id=d.game_id JOIN sys_user u ON u.id=d.author_id
        LEFT JOIN catalog_game gx ON gx.game_id=g.id LEFT JOIN catalog_guide x ON x.guide_id=d.id
        LEFT JOIN user_profile p ON p.user_id=u.id
        """;
    private static final String SELECT = """
        SELECT d.*,g.game_name,g.genre,g.cover_url,u.nickname,COALESCE(p.avatar,'') author_avatar,
          x.tags,x.cover,x.spoiler,x.featured,x.published_at,x.reviewed_at,x.review_reason,x.revision_of,
          (SELECT COUNT(*) FROM guide_like l WHERE l.guide_id=d.id) likes
        """;
    private static final Map<String, Integer> STATUS = Map.of("PENDING_REVIEW", 0, "PUBLISHED", 1, "REJECTED", 2, "DRAFT", 3, "OFF_SHELF", 4);
    private static final List<String> STATES = List.of("PENDING_REVIEW", "PUBLISHED", "REJECTED", "DRAFT", "OFF_SHELF");
    private final CatalogDatabase db;

    public CatalogGuideService(CatalogDatabase db) { this.db = db; }

    private Map<String, Object> record(Map<String, Object> row) {
        int status = (int) number(row.get("audit_status"), 0);
        return map("id", row.get("id"), "code", row.get("guide_code"), "title", row.get("title"),
                "description", text(row.get("summary")), "gameId", row.get("game_id"), "gameName", row.get("game_name"),
                "gameCategory", row.get("genre"), "gameCover", text(row.get("cover_url")), "category", row.get("category_tag"),
                "tags", db.strings(row.get("tags")), "author", row.get("nickname"), "authorId", row.get("author_id"),
                "avatar", text(row.get("author_avatar")), "cover", text(row.get("cover")).isEmpty() ? text(row.get("cover_url")) : row.get("cover"),
                "views", row.get("view_count"), "likes", row.get("likes"), "createdAt", iso(row.get("create_time")),
                "publishedAt", iso(row.get("published_at") == null && status == 1 ? row.get("create_time") : row.get("published_at")),
                "status", status >= 0 && status < STATES.size() ? STATES.get(status) : "OFF_SHELF",
                "body", row.get("content_md"), "spoiler", Boolean.TRUE.equals(row.get("spoiler")), "featured", Boolean.TRUE.equals(row.get("featured")),
                "revisionOf", row.get("revision_of"), "reviewedAt", iso(row.get("reviewed_at")), "reviewReason", text(row.get("review_reason")), "version", row.get("version"));
    }

    private Filter filter(Map<String, ?> params, boolean published, boolean reviews) {
        var filter = new Filter("gx.deleted_at IS NULL");
        if (published) filter.add("d.audit_status=1 AND COALESCE(gx.status,'PUBLISHED')='PUBLISHED'");
        else if (reviews) filter.add("d.audit_status IN (0,1,2)");
        String status = text(params, "status");
        if (!status.isEmpty() && !published) {
            if (!STATUS.containsKey(status)) invalid("未知的攻略状态");
            filter.add("d.audit_status=?", STATUS.get(status));
        }
        if (number(params.get("gameId"), 0) > 0) filter.add("d.game_id=?", number(params.get("gameId"), 0));
        if (!text(params, "keyword").isEmpty()) {
            String keyword = "%" + text(params, "keyword") + "%";
            filter.add("(d.title LIKE ? OR d.summary LIKE ? OR g.game_name LIKE ?)", keyword, keyword, keyword);
        }
        if (!text(params, "author").isEmpty()) filter.add("u.nickname LIKE ?", "%" + text(params, "author") + "%");
        if (!text(params, "category").isEmpty()) filter.add("d.category_tag=?", text(params, "category"));
        if (!text(params, "tag").isEmpty()) filter.add("JSON_CONTAINS(x.tags,JSON_QUOTE(?))", text(params, "tag"));
        if (!text(params, "publishedFrom").isEmpty()) filter.add("DATE(COALESCE(x.published_at,d.create_time))>=?", text(params, "publishedFrom"));
        if (!text(params, "publishedTo").isEmpty()) filter.add("DATE(COALESCE(x.published_at,d.create_time))<=?", text(params, "publishedTo"));
        return filter;
    }

    private String order(Map<String, ?> params) {
        return switch (text(params, "order")) {
            case "oldest" -> "d.create_time ASC,d.id ASC";
            case "views" -> "d.view_count DESC,d.id DESC";
            case "likes" -> "likes DESC,d.id DESC";
            default -> "d.create_time DESC,d.id DESC";
        };
    }

    public Map<String, Object> page(Map<String, ?> params, boolean published, boolean reviews) {
        int page = pageNumber(params), size = pageSize(params);
        var filter = filter(params, published, reviews);
        var records = db.jdbc.queryForList(SELECT + JOIN + filter.where() + " ORDER BY " + order(params) + " LIMIT ? OFFSET ?",
                filter.with(size, (page - 1) * size)).stream().map(this::record).toList();
        return CatalogDatabase.page(records, db.count("SELECT COUNT(*) " + JOIN + filter.where(), filter.args()), page, size);
    }

    public List<Map<String, Object>> export(Map<String, ?> params) {
        var filter = filter(params, false, Boolean.parseBoolean(text(params, "reviewQueue")));
        return db.jdbc.queryForList(SELECT + JOIN + filter.where() + " ORDER BY " + order(params) + " LIMIT 10000", filter.args())
                .stream().map(this::record).toList();
    }

    public Map<String, Object> get(long id, boolean published) {
        String active = published ? " AND d.audit_status=1 AND COALESCE(gx.status,'PUBLISHED')='PUBLISHED'" : "";
        return record(db.one(SELECT + JOIN + " WHERE d.id=? AND gx.deleted_at IS NULL" + active, id));
    }

    public Map<String, Object> editable(long id) {
        var revisions = db.jdbc.queryForList("SELECT guide_id FROM catalog_guide WHERE revision_of=?", id);
        return get(revisions.isEmpty() ? id : number(revisions.get(0).get("guide_id"), id), false);
    }

    public Map<String, Object> stats() {
        return db.one("""
            SELECT COUNT(*) total, COUNT(CASE WHEN audit_status=1 THEN 1 END) published,
              COUNT(CASE WHEN audit_status=3 THEN 1 END) draft, COUNT(CASE WHEN audit_status=0 THEN 1 END) pending,
              COUNT(CASE WHEN audit_status=4 THEN 1 END) offline, COUNT(CASE WHEN audit_status=2 THEN 1 END) rejected,
              COUNT(CASE WHEN d.create_time>=CURRENT_DATE THEN 1 END) addedToday,
              COUNT(CASE WHEN d.create_time>=CURRENT_DATE-INTERVAL 6 DAY THEN 1 END) addedThisWeek,
              COUNT(CASE WHEN audit_status=1 AND COALESCE(x.published_at,d.create_time)>=CURRENT_DATE-INTERVAL 6 DAY THEN 1 END) publishedThisWeek,
              COALESCE(SUM(d.view_count),0) totalViews
            """ + JOIN + " WHERE gx.deleted_at IS NULL");
    }

    @Transactional
    public Map<String, Object> save(Long id, Map<String, Object> input, boolean player) {
        long game = number(input.get("gameId"), 0);
        db.one("SELECT g.id FROM game_info g LEFT JOIN catalog_game x ON x.game_id=g.id WHERE g.id=? AND x.deleted_at IS NULL" +
                (player ? " AND COALESCE(x.status,'PUBLISHED')='PUBLISHED'" : "") + " FOR UPDATE", game);
        String title = field(input, "title", 150, true);
        String category = field(input, "category", 50, true);
        String summary = field(input, "description", 300, false);
        String body = field(input, "body", 120000, true);
        List<String> tags = list(input.get("tags"), 8, 50);
        Long revision = null;
        long authorId = UserContext.get().getId();
        if (id != null) {
            var original = lock(id, input.get("version"));
            if (number(original.get("audit_status"), 0) == 1) {
                if (db.count("SELECT COUNT(*) FROM catalog_guide WHERE revision_of=?", id) > 0) conflict("该攻略已有待处理的新版本");
                authorId = number(original.get("author_id"), authorId);
                revision = id;
                id = null;
            }
        }
        boolean create = id == null;
        if (create) {
            id = db.insert("INSERT INTO game_guide(guide_code,game_id,author_id,title,category_tag,summary,content_md,audit_status) VALUES(?,?,?,?,?,?,?,?)",
                    "GD-" + UUID.randomUUID().toString().replace("-", "").substring(0, 28), game,
                    authorId, title, category, summary, body, player ? 0 : 3);
        } else {
            Map<String, Object> old = lock(id, input.get("version"));
            int status = (int) number(old.get("audit_status"), 0);
            if (status == 0) conflict("待审核的攻略请先完成审核");
            db.jdbc.update("UPDATE game_guide SET game_id=?,title=?,category_tag=?,summary=?,content_md=?,version=version+1 WHERE id=?",
                    game, title, category, summary, body, id);
        }
        String cover = field(input, "cover", 255, false);
        db.jdbc.update("""
            INSERT INTO catalog_guide(guide_id,tags,cover,spoiler,revision_of) VALUES(?,?,?,?,?)
            ON DUPLICATE KEY UPDATE tags=VALUES(tags),cover=VALUES(cover),spoiler=VALUES(spoiler)
            """, id, db.json(tags), cover, Boolean.TRUE.equals(input.get("spoiler")), revision);
        if (!player) db.log("攻略管理", create ? "添加" : "编辑", title);
        return get(id, false);
    }

    @Transactional
    public void change(long id, String action, Map<String, Object> input) {
        var guide = lock(id, input.get("version"));
        int current = (int) number(guide.get("audit_status"), 0);
        var details = db.jdbc.queryForList("SELECT revision_of FROM catalog_guide WHERE guide_id=?", id);
        long original = details.isEmpty() ? 0 : number(details.get(0).get("revision_of"), 0);
        if ((action.equals("delete") || action.equals("off-shelf")) && db.count("SELECT COUNT(*) FROM catalog_guide WHERE revision_of=?", id) > 0)
            conflict("请先处理该攻略的新版本");
        switch (action) {
            case "delete" -> {
                if (current == 1 || current == 0) conflict("请先下架或审核该攻略");
                db.jdbc.update("DELETE FROM game_favorite WHERE guide_id=?", id);
                db.jdbc.update("DELETE FROM guide_like WHERE guide_id=?", id);
                db.jdbc.update("DELETE FROM browse_history WHERE target_type='guide' AND target_id=?", id);
                db.jdbc.update("DELETE FROM game_guide WHERE id=?", id);
            }
            case "submit-review" -> {
                if (current != 3 && current != 2 && current != 4) conflict("攻略当前状态不可送审");
                status(id, 0);
            }
            case "approve", "reject" -> {
                if (current != 0) conflict("攻略不在待审核状态");
                boolean approved = action.equals("approve");
                if (approved && original > 0) {
                    applyRevision(id, original);
                    db.log("攻略管理", action, guide.get("title"));
                    return;
                }
                status(id, approved ? 1 : 2);
                db.jdbc.update("""
                    INSERT INTO catalog_guide(guide_id,published_at,reviewed_at,review_reason) VALUES(?,?,NOW(),?)
                    ON DUPLICATE KEY UPDATE published_at=COALESCE(VALUES(published_at),published_at),reviewed_at=NOW(),review_reason=VALUES(review_reason)
                    """, id, approved ? java.time.LocalDateTime.now() : null, field(input, "reason", 500, !approved));
            }
            case "off-shelf" -> {
                if (current != 1) conflict("攻略当前状态不可下架");
                status(id, 4);
            }
            default -> invalid("未知的攻略操作");
        }
        db.log("攻略管理", action, guide.get("title"));
    }

    private Map<String, Object> lock(long id, Object version) {
        var row = db.one("SELECT id,title,author_id,audit_status,version FROM game_guide WHERE id=? FOR UPDATE", id);
        long expected = number(version, 0);
        if (expected > 0 && expected != number(row.get("version"), 1)) conflict("攻略已更新，请重新载入");
        return row;
    }

    private void applyRevision(long revision, long original) {
        if (number(lock(original, null).get("audit_status"), 0) != 1) conflict("原攻略状态已改变");
        db.jdbc.update("""
            UPDATE game_guide g JOIN game_guide r ON r.id=?
            SET g.game_id=r.game_id,g.title=r.title,g.category_tag=r.category_tag,g.summary=r.summary,
                g.content_md=r.content_md,g.version=g.version+1 WHERE g.id=?
            """, revision, original);
        db.jdbc.update("""
            INSERT INTO catalog_guide(guide_id,tags,cover,spoiler,reviewed_at)
            SELECT ?,tags,cover,spoiler,NOW() FROM catalog_guide WHERE guide_id=?
            ON DUPLICATE KEY UPDATE tags=VALUES(tags),cover=VALUES(cover),spoiler=VALUES(spoiler),reviewed_at=NOW()
            """, original, revision);
        db.jdbc.update("DELETE FROM game_guide WHERE id=?", revision);
    }

    private void status(long id, int status) {
        db.jdbc.update("UPDATE game_guide SET audit_status=?,version=version+1 WHERE id=?", status, id);
    }

    public List<Map<String, Object>> taxonomy(String kind) {
        String count = kind.equals("category")
                ? "(SELECT COUNT(*) FROM game_guide d WHERE d.category_tag=t.name)"
                : "(SELECT COUNT(*) FROM catalog_guide x WHERE JSON_CONTAINS(x.tags,JSON_QUOTE(t.name)))";
        return db.jdbc.queryForList("SELECT t.id,t.name," + count + " count FROM guide_taxonomy t WHERE t.kind=? ORDER BY t.id", kind);
    }

    @Transactional
    public void addTaxonomy(String kind, Map<String, Object> input) {
        String name = field(input, "name", 50, true);
        if (db.jdbc.update("INSERT IGNORE INTO guide_taxonomy(kind,name) VALUES(?,?)", kind, name) == 0)
            conflict("同名分类或标签已存在");
        db.log("攻略管理", "添加分类或标签", name);
    }

    @Transactional
    public void deleteTaxonomy(String kind, long id) {
        var row = taxonomy(kind).stream().filter(r -> number(r.get("id"), 0) == id).findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "分类或标签不存在"));
        if (number(row.get("count"), 0) > 0) conflict("该分类或标签正在使用");
        db.jdbc.update("DELETE FROM guide_taxonomy WHERE kind=? AND id=?", kind, id);
        db.log("攻略管理", "删除分类或标签", row.get("name"));
    }
}
