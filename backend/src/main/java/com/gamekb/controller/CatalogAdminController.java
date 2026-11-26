package com.gamekb.controller;

import com.gamekb.auth.RequireRole;
import com.gamekb.auth.UserContext;
import com.gamekb.common.BizException;
import com.gamekb.common.Result;
import com.gamekb.service.CatalogDatabase;
import com.gamekb.service.CatalogGameService;
import com.gamekb.service.CatalogGuideService;
import com.gamekb.service.CatalogUserService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static com.gamekb.service.CatalogDatabase.*;

@RequireRole("ADMIN")
@RestController
@RequestMapping("/api/admin/v1")
public class CatalogAdminController {
    private final CatalogDatabase db;
    private final CatalogGameService games;
    private final CatalogGuideService guides;
    private final CatalogUserService users;

    public CatalogAdminController(CatalogDatabase db, CatalogGameService games, CatalogGuideService guides, CatalogUserService users) {
        this.db = db;
        this.games = games;
        this.guides = guides;
        this.users = users;
    }

    @GetMapping("/session")
    public Result<?> session() {
        return Result.success(map("isAdmin", true, "name", UserContext.get().getNickname(), "permissions", List.of(
                "dashboard:read", "games:read", "games:write", "games:review", "games:batch", "games:delete", "games:import", "games:export",
                "guides:read", "guides:write", "guides:review", "guides:categories", "guides:batch", "guides:delete",
                "users:read", "users:write", "users:ban")));
    }

    @GetMapping("/games")
    public Result<?> gamePage(@RequestParam Map<String, String> query) { return Result.success(games.page(query, false)); }
    @GetMapping("/games/options")
    public Result<?> gameOptions() { return Result.success(games.options()); }
    @GetMapping("/games/stats")
    public Result<?> gameStats() { return Result.success(games.stats()); }
    @GetMapping("/games/{id}")
    public Result<?> game(@PathVariable long id) { return Result.success(games.get(id, false)); }
    @PostMapping("/games")
    public Result<?> createGame(@RequestBody Map<String, Object> body) { return Result.success(games.save(null, body)); }
    @PutMapping("/games/{id}")
    public Result<?> updateGame(@PathVariable long id, @RequestBody Map<String, Object> body) { return Result.success(games.save(id, body)); }
    @DeleteMapping("/games/{id}")
    public Result<?> deleteGame(@PathVariable long id) { games.change(id, "delete", List.of()); return Result.success(null); }
    @PostMapping("/games/{id}/{action:submit-review|publish|off-shelf|reject}")
    public Result<?> changeGame(@PathVariable long id, @PathVariable String action) {
        games.change(id, action, List.of());
        return Result.success(null);
    }

    @PostMapping("/games/batch")
    public Result<?> batchGames(@RequestBody Map<String, Object> body) {
        List<Long> ids = ids(body.get("ids"));
        var errors = new ArrayList<Map<String, Object>>();
        String action = text(body, "action");
        List<String> values = list(body.get("values"), 8, 50);
        for (long id : ids) {
            try { games.change(id, action, values); }
            catch (BizException e) { errors.add(map("id", id, "reason", e.getMessage())); }
        }
        return Result.success(map("succeeded", ids.size() - errors.size(), "failed", errors.size(), "errors", errors));
    }

    public static List<Long> ids(Object value) {
        if (value == null) return List.of();
        if (!(value instanceof List<?> rows) || rows.size() > 1000) throw new BizException("请选择 1 至 1000 条记录");
        var result = new LinkedHashSet<Long>();
        for (Object row : rows) {
            long id = number(row, 0);
            if (id <= 0) invalid("无效的记录编号");
            result.add(id);
        }
        return new ArrayList<>(result);
    }

    @GetMapping("/guides")
    public Result<?> guidePage(@RequestParam Map<String, String> query) { return Result.success(guides.page(query, false, false)); }
    @GetMapping("/guide-reviews")
    public Result<?> reviewPage(@RequestParam Map<String, String> query) { return Result.success(guides.page(query, false, true)); }
    @GetMapping("/guides/stats")
    public Result<?> guideStats() { return Result.success(guides.stats()); }
    @GetMapping("/guides/export")
    public Result<?> exportGuides(@RequestParam Map<String, String> query) { return Result.success(guides.export(query)); }
    @PostMapping("/guides/import")
    @Transactional
    public Result<?> importGuides(@RequestBody Map<String, List<Map<String, Object>>> body) {
        var rows = body.get("records");
        if (rows == null || rows.isEmpty() || rows.size() > 500) invalid("请选择 1 至 500 条攻略");
        for (var row : rows) guides.save(null, row, false);
        return Result.success(null);
    }
    @GetMapping("/guides/{id}")
    public Result<?> guide(@PathVariable long id, @RequestParam(defaultValue = "false") boolean forEdit) {
        return Result.success(forEdit ? guides.editable(id) : guides.get(id, false));
    }
    @PostMapping("/guides")
    public Result<?> createGuide(@RequestBody Map<String, Object> body) { return Result.success(guides.save(null, body, false)); }
    @PutMapping("/guides/{id}")
    public Result<?> updateGuide(@PathVariable long id, @RequestBody Map<String, Object> body) { return Result.success(guides.save(id, body, false)); }
    @DeleteMapping("/guides/{id}")
    public Result<?> deleteGuide(@PathVariable long id, @RequestBody(required = false) Map<String, Object> body) {
        guides.change(id, "delete", body == null ? Map.of() : body);
        return Result.success(null);
    }
    @PostMapping({"/guides/{id}/{action:submit-review|off-shelf}", "/guide-reviews/{id}/{action:approve|reject}"})
    public Result<?> changeGuide(@PathVariable long id, @PathVariable String action, @RequestBody(required = false) Map<String, Object> body) {
        guides.change(id, action, body == null ? Map.of() : body);
        return Result.success(null);
    }
    @GetMapping("/guide-{taxonomy:categories|tags}")
    public Result<?> taxonomy(@PathVariable String taxonomy) { return Result.success(guides.taxonomy(taxonomy.equals("categories") ? "category" : "tag")); }
    @PostMapping("/guide-{taxonomy:categories|tags}")
    public Result<?> addTaxonomy(@PathVariable String taxonomy, @RequestBody Map<String, Object> body) {
        guides.addTaxonomy(taxonomy.equals("categories") ? "category" : "tag", body);
        return Result.success(null);
    }
    @DeleteMapping("/guide-{taxonomy:categories|tags}/{id}")
    public Result<?> deleteTaxonomy(@PathVariable String taxonomy, @PathVariable long id) {
        guides.deleteTaxonomy(taxonomy.equals("categories") ? "category" : "tag", id);
        return Result.success(null);
    }

    @GetMapping("/users")
    public Result<?> userPage(@RequestParam Map<String, String> query) { return Result.success(users.page(query)); }
    @GetMapping("/users/{id}")
    public Result<?> user(@PathVariable long id) { return Result.success(users.detail(id)); }
    @GetMapping("/users/stats")
    public Result<?> userStats() { return Result.success(users.stats()); }
    @GetMapping("/users/growth")
    public Result<?> userGrowth(@RequestParam(defaultValue = "7") int days) { return Result.success(users.growth(days(days))); }
    @GetMapping("/users/distribution")
    public Result<?> usersByMembership() { return Result.success(users.distribution()); }
    @GetMapping("/users/active-ranking")
    public Result<?> userRanking() { return Result.success(users.ranking()); }
    @GetMapping("/user-levels")
    public Result<?> levels() {
        return Result.success(db.jdbc.queryForList("SELECT level,name,required_experience requiredExperience,rule FROM user_level_rule ORDER BY level"));
    }
    @PatchMapping("/users/{id}/profile")
    public Result<?> renameUser(@PathVariable long id, @RequestBody Map<String, Object> body) {
        users.rename(id, body);
        return Result.success(null);
    }
    @PostMapping("/users/{id}/ban")
    public Result<?> ban(@PathVariable long id, @RequestBody Map<String, Object> body) {
        users.ban(id, true, body);
        return Result.success(null);
    }
    @PostMapping("/users/{id}/unban")
    public Result<?> unban(@PathVariable long id) {
        users.ban(id, false, Map.of());
        return Result.success(null);
    }

    /**
     * 管理员重置用户密码。不传 newPassword 时生成 10 位随机密码，
     * 明文只在本次响应里返回一次，库里存 BCrypt 密文。
     */
    @PostMapping("/users/{id}/password")
    public Result<?> resetPassword(@PathVariable long id,
                                   @RequestBody(required = false) Map<String, Object> body) {
        String raw = body == null ? null : String.valueOf(body.getOrDefault("newPassword", ""));
        return Result.success(map("password", users.resetPassword(id, raw)));
    }

    @GetMapping("/preferences")
    public Result<?> preferences() {
        long id = UserContext.get().getId();
        var rows = db.jdbc.queryForList("SELECT sidebar_collapsed,page_size,dashboard_period FROM admin_preference WHERE user_id=?", id);
        if (rows.isEmpty()) return Result.success(map("sidebarCollapsed", false, "pageSize", 20, "dashboardPeriod", 7));
        var row = rows.get(0);
        return Result.success(map("sidebarCollapsed", Boolean.TRUE.equals(row.get("sidebar_collapsed")) ||
                "1".equals(text(row.get("sidebar_collapsed"))), "pageSize", row.get("page_size"), "dashboardPeriod", row.get("dashboard_period")));
    }
    @PutMapping("/preferences")
    public Result<?> savePreferences(@RequestBody Map<String, Object> input) {
        int pageSize = (int) number(input.get("pageSize"), 20);
        int period = (int) number(input.get("dashboardPeriod"), 7);
        if (!List.of(20, 50, 100).contains(pageSize) || !List.of(7, 30, 90).contains(period)) invalid("设置值有误");
        db.jdbc.update("""
            INSERT INTO admin_preference(user_id,sidebar_collapsed,page_size,dashboard_period) VALUES(?,?,?,?)
            ON DUPLICATE KEY UPDATE sidebar_collapsed=VALUES(sidebar_collapsed),page_size=VALUES(page_size),dashboard_period=VALUES(dashboard_period)
            """, UserContext.get().getId(), Boolean.TRUE.equals(input.get("sidebarCollapsed")), pageSize, period);
        return preferences();
    }

    @GetMapping("/dashboard/summary")
    public Result<?> summary() {
        return Result.success(map("totals", dashboardTotals(""), "change7d", dashboardTotals(" AND create_time>=CURRENT_DATE-INTERVAL 6 DAY"),
                "pendingGuides", db.count("SELECT COUNT(*) FROM game_guide WHERE audit_status=0")));
    }

    private Map<String, Object> dashboardTotals(String recent) {
        long gameCount = db.count("SELECT COUNT(*) FROM game_info WHERE id NOT IN (SELECT game_id FROM catalog_game WHERE deleted_at IS NOT NULL)" + recent);
        long guideCount = db.count("SELECT COUNT(*) FROM game_guide WHERE 1=1" + recent);
        long userCount = db.count("SELECT COUNT(*) FROM sys_user WHERE 1=1" + recent);
        return map("games", gameCount, "guides", guideCount, "users", userCount, "content", gameCount + guideCount);
    }

    @GetMapping("/dashboard/trends")
    public Result<?> trends(@RequestParam(defaultValue = "7") int days) {
        int daysCount = days(days);
        LocalDate first = LocalDate.now().minusDays(daysCount - 1L);
        var data = db.jdbc.queryForList("""
            SELECT DATE(create_time) date, COUNT(*) count, 'games' kind FROM game_info
             WHERE create_time>=? AND id NOT IN (SELECT game_id FROM catalog_game WHERE deleted_at IS NOT NULL) GROUP BY DATE(create_time)
            UNION ALL
            SELECT DATE(create_time),COUNT(*),'guides' FROM game_guide WHERE create_time>=? GROUP BY DATE(create_time)
            """, first, first);
        return Result.success(first.datesUntil(first.plusDays(daysCount)).map(day -> {
            var row = map("date", day.toString(), "games", 0, "guides", 0);
            data.stream().filter(item -> day.toString().equals(iso(item.get("date"))))
                    .forEach(item -> row.put(text(item, "kind"), item.get("count")));
            return row;
        }).toList());
    }

    @GetMapping("/dashboard/game-distribution")
    public Result<?> distribution() {
        return Result.success(db.jdbc.queryForList("""
            SELECT COALESCE(NULLIF(g.genre,''),'其他') name,COUNT(*) value FROM game_info g
            LEFT JOIN catalog_game x ON x.game_id=g.id WHERE x.deleted_at IS NULL GROUP BY name ORDER BY value DESC,name
            """));
    }

    @GetMapping("/dashboard/recent")
    public Result<?> recent(@RequestParam(defaultValue = "games") String type, @RequestParam(defaultValue = "5") int limit) {
        String sql = switch (type) {
            case "games" -> """
                SELECT g.id,g.game_name name,g.genre secondary,g.cover_url image,g.create_time date FROM game_info g
                LEFT JOIN catalog_game x ON x.game_id=g.id WHERE x.deleted_at IS NULL ORDER BY g.id DESC LIMIT ?
                """;
            case "users" -> """
                SELECT u.id,u.nickname name,u.role secondary,p.avatar image,u.create_time date FROM sys_user u
                LEFT JOIN user_profile p ON p.user_id=u.id ORDER BY u.id DESC LIMIT ?
                """;
            case "guides" -> """
                SELECT d.id,d.title name,g.game_name secondary,COALESCE(x.cover,g.cover_url) image,d.create_time date
                FROM game_guide d JOIN game_info g ON g.id=d.game_id LEFT JOIN catalog_guide x ON x.guide_id=d.id ORDER BY d.id DESC LIMIT ?
                """;
            default -> throw new BizException("未知的内容类型");
        };
        return Result.success(db.jdbc.queryForList(sql, Math.max(1, Math.min(limit, 50))).stream().map(row -> {
            row.put("date", iso(row.get("date")));
            return row;
        }).toList());
    }

    @GetMapping("/dashboard/logs")
    public Result<?> logs(@RequestParam(defaultValue = "30") int limit) {
        return Result.success(db.jdbc.queryForList("""
            SELECT id,actor,module,action,target,create_time time,'成功' result FROM admin_activity ORDER BY id DESC LIMIT ?
            """, Math.max(1, Math.min(limit, 100))).stream().map(row -> {
            row.put("time", iso(row.get("time")));
            return row;
        }).toList());
    }

    @GetMapping("/dashboard/health")
    public Result<?> health() {
        db.count("SELECT 1");
        return Result.success(Map.of("api", "up", "database", "up"));
    }

    private int days(int value) {
        if (!List.of(7, 30, 90).contains(value)) invalid("时间范围有误");
        return value;
    }
}
