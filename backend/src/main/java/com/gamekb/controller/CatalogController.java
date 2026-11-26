package com.gamekb.controller;

import com.gamekb.auth.UserContext;
import com.gamekb.common.Result;
import com.gamekb.service.CatalogDatabase;
import com.gamekb.service.CatalogGameService;
import com.gamekb.service.CatalogGuideService;
import com.gamekb.service.CatalogUserService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import static com.gamekb.service.CatalogDatabase.*;

@RestController
@RequestMapping("/api/catalog")
public class CatalogController {
    private final CatalogDatabase db;
    private final CatalogGameService games;
    private final CatalogGuideService guides;
    private final CatalogUserService users;

    public CatalogController(CatalogDatabase db, CatalogGameService games, CatalogGuideService guides, CatalogUserService users) {
        this.db = db;
        this.games = games;
        this.guides = guides;
        this.users = users;
    }

    @GetMapping("/games")
    public Result<?> games(@RequestParam Map<String, String> query) { return Result.success(games.page(query, true)); }

    @GetMapping("/options")
    public Result<?> options() { return Result.success(games.options()); }

    @GetMapping("/games/{id}")
    public Result<?> game(@PathVariable long id) {
        long user = user();
        var game = games.get(id, true);
        game.put("collected", db.count("SELECT COUNT(*) FROM user_game_favorite WHERE user_id=? AND game_id=?", user, id) > 0);
        return Result.success(game);
    }

    @GetMapping("/guides")
    public Result<?> guides(@RequestParam Map<String, String> query) { return Result.success(guides.page(query, true, false)); }

    @GetMapping("/guides/{id}")
    public Result<?> guide(@PathVariable long id) {
        long user = user();
        var guide = guides.get(id, true);
        guide.put("collected", db.count("SELECT COUNT(*) FROM game_favorite WHERE user_id=? AND guide_id=?", user, id) > 0);
        guide.put("liked", db.count("SELECT COUNT(*) FROM guide_like WHERE user_id=? AND guide_id=?", user, id) > 0);
        guide.put("following", db.count("SELECT COUNT(*) FROM user_follow WHERE user_id=? AND author_id=?", user, guide.get("authorId")) > 0);
        return Result.success(guide);
    }

    @PostMapping("/guides")
    public Result<?> submit(@RequestBody Map<String, Object> input) { return Result.success(guides.save(null, input, true)); }

    @GetMapping("/profile")
    public Result<?> profile() {
        long id = user();
        var profile = users.profile(id);
        profile.put("following", db.count("SELECT COUNT(*) FROM user_follow WHERE user_id=?", id));
        return Result.success(profile);
    }

    @PatchMapping("/profile")
    public Result<?> updateProfile(@RequestBody Map<String, Object> input) { return Result.success(users.updateProfile(user(), input)); }

    @GetMapping("/favorites")
    public Result<?> favorites() {
        long user = user();
        var gameIds = db.jdbc.queryForList("""
            SELECT g.id,f.create_time collected_at FROM user_game_favorite f JOIN game_info g ON g.id=f.game_id
            LEFT JOIN catalog_game x ON x.game_id=g.id
            WHERE f.user_id=? AND x.deleted_at IS NULL AND COALESCE(x.status,'PUBLISHED')='PUBLISHED'
            ORDER BY f.create_time DESC,g.id DESC
            """, user);
        var guideIds = db.jdbc.queryForList("""
            SELECT d.id FROM game_favorite f JOIN game_guide d ON d.id=f.guide_id
            JOIN game_info g ON g.id=d.game_id LEFT JOIN catalog_game x ON x.game_id=g.id
            WHERE f.user_id=? AND d.audit_status=1 AND x.deleted_at IS NULL AND COALESCE(x.status,'PUBLISHED')='PUBLISHED'
            ORDER BY f.create_time DESC,d.id DESC
            """, Long.class, user);
        return Result.success(map("games", gameIds.stream().map(row -> {
                    var game = games.get(number(row.get("id"), 0), true);
                    game.put("collectedAt", iso(row.get("collected_at")));
                    return game;
                }).toList(),
                "guides", guideIds.stream().map(id -> guides.get(id, true)).toList()));
    }

    @PutMapping("/favorites/{type:game|guide}/{id}")
    @Transactional
    public Result<?> favorite(@PathVariable String type, @PathVariable long id, @RequestBody Map<String, Boolean> body) {
        boolean active = Boolean.TRUE.equals(body.get("active"));
        long user = user();
        if (type.equals("game")) {
            if (active) {
                games.get(id, true);
                db.jdbc.update("INSERT IGNORE INTO user_game_favorite(user_id,game_id) VALUES(?,?)", user, id);
            } else db.jdbc.update("DELETE FROM user_game_favorite WHERE user_id=? AND game_id=?", user, id);
        } else if (active) {
            guides.get(id, true);
            db.jdbc.update("INSERT IGNORE INTO game_favorite(user_id,guide_id) VALUES(?,?)", user, id);
        } else db.jdbc.update("DELETE FROM game_favorite WHERE user_id=? AND guide_id=?", user, id);
        return Result.success(Map.of("active", active));
    }

    @PutMapping("/guides/{id}/like")
    @Transactional
    public Result<?> like(@PathVariable long id, @RequestBody Map<String, Boolean> body) {
        guides.get(id, true);
        boolean active = Boolean.TRUE.equals(body.get("active"));
        if (active) db.jdbc.update("INSERT IGNORE INTO guide_like(user_id,guide_id) VALUES(?,?)", user(), id);
        else db.jdbc.update("DELETE FROM guide_like WHERE user_id=? AND guide_id=?", user(), id);
        return Result.success(map("active", active, "likes", db.count("SELECT COUNT(*) FROM guide_like WHERE guide_id=?", id)));
    }

    @PutMapping("/authors/{id}/follow")
    public Result<?> follow(@PathVariable long id, @RequestBody Map<String, Boolean> body) {
        long user = user();
        if (id == user) invalid("不能关注自己的账号");
        boolean active = Boolean.TRUE.equals(body.get("active"));
        if (active) {
            db.one("SELECT id FROM sys_user WHERE id=?", id);
            db.jdbc.update("INSERT IGNORE INTO user_follow(user_id,author_id) VALUES(?,?)", user, id);
        } else db.jdbc.update("DELETE FROM user_follow WHERE user_id=? AND author_id=?", user, id);
        return Result.success(Map.of("active", active));
    }

    @GetMapping("/history")
    public Result<?> history() {
        return Result.success(db.jdbc.queryForList("""
            SELECT h.id,h.target_type type,h.target_id targetId,h.create_time time,
              CASE WHEN h.target_type='guide' THEN d.title ELSE g.game_name END title,g.genre subtitle
            FROM browse_history h LEFT JOIN game_guide d ON h.target_type='guide' AND d.id=h.target_id
            JOIN game_info g ON g.id=CASE WHEN h.target_type='guide' THEN d.game_id ELSE h.target_id END
            LEFT JOIN catalog_game x ON x.game_id=g.id WHERE h.user_id=? AND x.deleted_at IS NULL
              AND COALESCE(x.status,'PUBLISHED')='PUBLISHED' AND (h.target_type='game' OR d.audit_status=1)
            ORDER BY h.create_time DESC,h.id DESC
            """, user()).stream().map(row -> {
                row.put("time", iso(row.get("time")));
                return row;
            }).toList());
    }

    @PostMapping("/history/{type:game|guide}/{id}")
    @Transactional
    public Result<?> visited(@PathVariable String type, @PathVariable long id) {
        if (type.equals("guide")) {
            guides.get(id, true);
            db.jdbc.update("UPDATE game_guide SET view_count=view_count+1 WHERE id=?", id);
        } else games.get(id, true);
        db.jdbc.update("""
            INSERT INTO browse_history(user_id,target_type,target_id) VALUES(?,?,?)
            ON DUPLICATE KEY UPDATE create_time=NOW()
            """, user(), type, id);
        return Result.success(null);
    }

    @DeleteMapping("/history")
    public Result<?> clearHistory() {
        db.jdbc.update("DELETE FROM browse_history WHERE user_id=?", user());
        return Result.success(null);
    }

    private long user() { return UserContext.get().getId(); }
}
