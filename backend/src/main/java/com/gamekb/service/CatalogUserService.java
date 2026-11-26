package com.gamekb.service;

import com.gamekb.auth.UserContext;
import com.gamekb.common.BizException;
import com.gamekb.common.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.gamekb.service.CatalogDatabase.*;

@Service
public class CatalogUserService {
    private static final String JOIN = " FROM sys_user u LEFT JOIN user_profile p ON p.user_id=u.id";
    private static final String LEVEL = "COALESCE((SELECT MAX(level) FROM user_level_rule r WHERE r.required_experience<=COALESCE(p.experience,0)),1)";
    private static final String MEMBERSHIP = "COALESCE(p.membership,'standard')";
    private static final String STATUS = "CASE WHEN u.status=2 AND (p.banned_until IS NULL OR p.banned_until>NOW()) THEN 'BANNED' WHEN u.status=0 THEN 'DISABLED' ELSE 'NORMAL' END";
    private static final String COLUMNS = """
         SELECT u.id,u.username,u.nickname,u.role,u.create_time,p.avatar,p.email,p.phone,p.bio,p.location,
                p.last_login_at,p.experience,
        """ + LEVEL + " level," + MEMBERSHIP + " membership," + STATUS + " status";
    private final CatalogDatabase db;
    private final org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder encoder;

    public CatalogUserService(CatalogDatabase db,
                              org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder encoder) {
        this.db = db;
        this.encoder = encoder;
    }

    /** 管理员重置密码：不传则生成随机密码，返回明文仅此一次 */
    public String resetPassword(long id, String rawPassword) {
        String password = rawPassword == null || rawPassword.isBlank() ? randomPassword() : rawPassword.trim();
        if (password.length() < 6 || password.length() > 32) {
            throw new BizException("密码长度需在 6-32 位之间");
        }
        // 用户不存在时 one() 会抛 404
        var user = db.one("SELECT username FROM sys_user WHERE id=?", id);
        db.jdbc.update("UPDATE sys_user SET password=?, update_time=NOW() WHERE id=?",
                encoder.encode(password), id);
        db.log("users", "重置密码", "用户 " + text(user, "username"));
        return password;
    }

    private static String randomPassword() {
        // 去掉容易混淆的 0/O/1/l/I
        final String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
        var random = new java.security.SecureRandom();
        var builder = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            builder.append(chars.charAt(random.nextInt(chars.length())));
        }
        return builder.toString();
    }

    private Map<String, Object> view(Map<String, Object> row) {
        return map("id", row.get("id"), "username", row.get("username"), "name", row.get("nickname"),
                "avatar", text(row.get("avatar")), "role", row.get("role"), "level", row.get("level"),
                "membership", row.get("membership"), "status", row.get("status"),
                "registeredAt", iso(row.get("create_time")), "lastLoginAt", iso(row.get("last_login_at")));
    }

    public Map<String, Object> page(Map<String, ?> params) {
        var filter = new Filter("1=1");
        if (!text(params, "keyword").isEmpty()) {
            String key = "%" + text(params, "keyword") + "%";
            filter.add("(u.username LIKE ? OR u.nickname LIKE ?)", key, key);
        }
        switch (text(params, "tab")) {
            case "standard" -> filter.add(MEMBERSHIP + "='standard'");
            case "vip" -> filter.add(MEMBERSHIP + "<>'standard'");
            case "pending" -> filter.add("1=0");
            case "banned" -> filter.add("(" + STATUS + ")='BANNED'");
            default -> { }
        }
        if (!text(params, "status").isEmpty()) filter.add("(" + STATUS + ")=?", text(params, "status"));
        if (!text(params, "demoRole").isEmpty()) {
            String role = switch (text(params, "demoRole")) {
                case "admin" -> "ADMIN"; case "moderator" -> "MODERATOR"; default -> "PLAYER";
            };
            filter.add("u.role=?", role);
        }
        if (!text(params, "minLevel").isEmpty()) filter.add(LEVEL + ">=?", number(params.get("minLevel"), 1));
        if (!text(params, "maxLevel").isEmpty()) filter.add(LEVEL + "<=?", number(params.get("maxLevel"), 5));
        for (String[] date : List.of(new String[]{"registeredFrom", "DATE(u.create_time)>=?"}, new String[]{"registeredTo", "DATE(u.create_time)<=?"},
                new String[]{"lastLoginFrom", "DATE(p.last_login_at)>=?"}, new String[]{"lastLoginTo", "DATE(p.last_login_at)<=?"})) {
            if (!text(params, date[0]).isEmpty()) filter.add(date[1], text(params, date[0]));
        }
        String sort = switch (text(params, "sort")) {
            case "oldest" -> "u.create_time ASC,u.id ASC";
            case "login" -> "p.last_login_at DESC,u.id DESC";
            case "level" -> "level DESC,u.id DESC";
            default -> "u.create_time DESC,u.id DESC";
        };
        int page = pageNumber(params), size = pageSize(params);
        var records = db.jdbc.queryForList(COLUMNS + JOIN + filter.where() + " ORDER BY " + sort + " LIMIT ? OFFSET ?",
                filter.with(size, (page - 1) * size)).stream().map(this::view).toList();
        return CatalogDatabase.page(records, db.count("SELECT COUNT(*)" + JOIN + filter.where(), filter.args()), page, size);
    }

    public Map<String, Object> detail(long id) {
        var row = db.one(COLUMNS + JOIN + " WHERE u.id=?", id);
        var result = view(row);
        result.put("experience", number(row.get("experience"), 0));
        result.put("guideContributions", db.count("SELECT COUNT(*) FROM game_guide WHERE author_id=?", id));
        result.put("communityContributions", db.count("SELECT COUNT(*) FROM guide_like WHERE user_id=?", id));
        result.put("banHistory", db.jdbc.queryForList("""
            SELECT action,operator,reason,operated_at operatedAt,expires_at expiresAt FROM user_ban_log
             WHERE user_id=? ORDER BY id DESC
            """, id).stream().map(log -> {
                log.put("operatedAt", iso(log.get("operatedAt")));
                log.put("expiresAt", iso(log.get("expiresAt")));
                return log;
            }).toList());
        String email = text(row.get("email"));
        int at = email.lastIndexOf('@');
        if (at > 0) result.put("maskedEmail", email.substring(0, 1) + "***" + email.substring(at));
        return result;
    }

    public Map<String, Object> profile(long id) {
        var row = db.one(COLUMNS + JOIN + " WHERE u.id=?", id);
        return map("id", id, "nickname", row.get("nickname"), "username", row.get("username"), "role", row.get("role"),
                "level", row.get("level"), "title", db.jdbc.queryForObject("SELECT name FROM user_level_rule WHERE level=?", String.class, row.get("level")),
                "avatar", text(row.get("avatar")), "email", text(row.get("email")), "phone", text(row.get("phone")),
                "bio", text(row.get("bio")), "location", text(row.get("location")),
                "joinDate", iso(row.get("create_time")),
                "contributions", db.count("SELECT COUNT(*) FROM game_guide WHERE author_id=? AND audit_status=1", id),
                "receivedLikes", db.count("SELECT COUNT(*) FROM guide_like l JOIN game_guide d ON d.id=l.guide_id WHERE d.author_id=?", id));
    }

    @Transactional
    public Map<String, Object> updateProfile(long id, Map<String, Object> input) {
        String nickname = field(input, "nickname", 50, true);
        db.jdbc.update("UPDATE sys_user SET nickname=? WHERE id=?", nickname, id);
        db.jdbc.update("""
            INSERT INTO user_profile(user_id,email,phone,bio,location) VALUES(?,?,?,?,?)
            ON DUPLICATE KEY UPDATE email=VALUES(email),phone=VALUES(phone),bio=VALUES(bio),location=VALUES(location)
            """, id, field(input, "email", 190, false), field(input, "phone", 24, false),
                field(input, "bio", 80, false), field(input, "location", 120, false));
        return profile(id);
    }

    @Transactional
    public void rename(long id, Map<String, Object> input) {
        db.one("SELECT id FROM sys_user WHERE id=? FOR UPDATE", id);
        db.jdbc.update("UPDATE sys_user SET nickname=? WHERE id=?", field(input, "name", 50, true), id);
        db.log("用户管理", "编辑", id);
    }

    @Transactional
    public void ban(long id, boolean ban, Map<String, Object> input) {
        var user = db.one("SELECT id,role,status FROM sys_user WHERE id=? FOR UPDATE", id);
        if (text(user, "role").equals("ADMIN")) throw new BizException(ErrorCode.FORBIDDEN, "不能封禁管理员账号");
        int status = (int) number(user.get("status"), 0);
        if (ban && status == 0) conflict("账号处于禁用状态");
        if (!ban && status != 2) conflict("账号未被封禁");
        long duration = number(input.get("durationDays"), 0);
        if (duration < 0 || duration > 3650) invalid("封禁时长有误");
        var until = ban && duration > 0 ? LocalDateTime.now().plusDays(duration) : null;
        db.jdbc.update("INSERT INTO user_profile(user_id,banned_until) VALUES(?,?) ON DUPLICATE KEY UPDATE banned_until=VALUES(banned_until)", id, until);
        db.jdbc.update("UPDATE sys_user SET status=? WHERE id=?", ban ? 2 : 1, id);
        db.jdbc.update("INSERT INTO user_ban_log(user_id,operator,action,reason,expires_at) VALUES(?,?,?,?,?)",
                id, UserContext.get().getUsername(), ban ? "BAN" : "UNBAN", field(input, "reason", 500, ban), until);
        db.log("用户管理", ban ? "封禁" : "解封", id);
    }

    public void restoreExpiredBan(long id) {
        db.jdbc.update("""
            UPDATE sys_user u JOIN user_profile p ON p.user_id=u.id
            SET u.status=1,p.banned_until=NULL
            WHERE u.id=? AND u.status=2 AND p.banned_until<=NOW()
            """, id);
    }

    public void recordLogin(long id) {
        db.jdbc.update("INSERT INTO user_profile(user_id,last_login_at) VALUES(?,NOW()) ON DUPLICATE KEY UPDATE last_login_at=NOW()", id);
    }

    public Map<String, Object> stats() {
        return db.one("""
            SELECT COUNT(*) total,COUNT(CASE WHEN u.create_time>=CURRENT_DATE-INTERVAL 6 DAY THEN 1 END) newLast7Days,
                COUNT(CASE WHEN p.last_login_at>=CURRENT_DATE-INTERVAL 6 DAY THEN 1 END) activeLast7Days,
                COUNT(CASE WHEN p.membership<>'standard' THEN 1 END) vip, NULL pending,
                COUNT(CASE WHEN u.status=2 AND (p.banned_until IS NULL OR p.banned_until>NOW()) THEN 1 END) banned
            """ + JOIN);
    }

    public List<Map<String, Object>> growth(int days) {
        LocalDate first = LocalDate.now().minusDays(days - 1L);
        var counts = db.jdbc.queryForList("SELECT DATE(create_time) date,COUNT(*) total FROM sys_user WHERE create_time>=? GROUP BY DATE(create_time)", Date.valueOf(first));
        return first.datesUntil(first.plusDays(days)).map(date -> map("date", date.toString(), "newUsers", counts.stream()
                .filter(row -> date.toString().equals(iso(row.get("date")))).mapToLong(row -> number(row.get("total"), 0)).findFirst().orElse(0))).toList();
    }

    public List<Map<String, Object>> distribution() {
        return db.jdbc.queryForList("SELECT " + MEMBERSHIP + " membership, COUNT(*) count" + JOIN + " GROUP BY " + MEMBERSHIP);
    }

    public List<Map<String, Object>> ranking() {
        return db.jdbc.queryForList("""
            SELECT u.id,u.nickname name,COALESCE(p.avatar,'') avatar,c.contribution,
                   ROW_NUMBER() OVER (ORDER BY c.contribution DESC,u.id) `rank`
            FROM (SELECT author_id,COUNT(*) contribution FROM game_guide WHERE audit_status=1 GROUP BY author_id) c
            JOIN sys_user u ON u.id=c.author_id LEFT JOIN user_profile p ON p.user_id=u.id
            ORDER BY c.contribution DESC,u.id LIMIT 10
            """);
    }
}
