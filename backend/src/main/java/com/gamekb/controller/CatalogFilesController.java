package com.gamekb.controller;

import cn.hutool.core.text.csv.CsvUtil;
import com.alibaba.excel.EasyExcel;
import com.gamekb.auth.RequireRole;
import com.gamekb.auth.UserContext;
import com.gamekb.common.BizException;
import com.gamekb.common.Result;
import com.gamekb.service.CatalogDatabase;
import com.gamekb.service.CatalogGameService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.gamekb.service.CatalogDatabase.*;

@RestController
public class CatalogFilesController {
    private final CatalogDatabase db;
    private final CatalogGameService games;

    public CatalogFilesController(CatalogDatabase db, CatalogGameService games) {
        this.db = db;
        this.games = games;
    }

    @PostMapping("/api/admin/v1/uploads/images")
    @RequireRole("ADMIN")
    public Result<?> upload(@RequestParam MultipartFile file) throws IOException {
        if (file.isEmpty() || file.getSize() > 1024 * 1024) invalid("请选择 1 MB 以内的图片");
        byte[] bytes = file.getBytes();
        String type;
        if (starts(bytes, 0, 255, 216, 255)) type = "image/jpeg";
        else if (starts(bytes, 0, 137, 80, 78, 71, 13, 10, 26, 10)) type = "image/png";
        else if (starts(bytes, 0, 82, 73, 70, 70) && starts(bytes, 8, 87, 69, 66, 80)) type = "image/webp";
        else throw new BizException("仅支持 JPG、PNG 和 WebP 图片");
        String id = UUID.randomUUID().toString();
        db.jdbc.update("INSERT INTO catalog_media(id,content_type,image) VALUES(?,?,?)", id, type, bytes);
        return Result.success(Map.of("url", "/catalog/media/" + id));
    }

    @GetMapping("/catalog/media/{id}")
    public ResponseEntity<byte[]> image(@PathVariable UUID id) {
        var image = db.one("SELECT content_type,image FROM catalog_media WHERE id=?", id.toString());
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(text(image, "content_type")))
                .header("X-Content-Type-Options", "nosniff").cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePublic())
                .body((byte[]) image.get("image"));
    }

    private boolean starts(byte[] bytes, int offset, int... pattern) {
        if (bytes.length < offset + pattern.length) return false;
        for (int i = 0; i < pattern.length; i++) if ((bytes[offset + i] & 255) != pattern[i]) return false;
        return true;
    }

    @PostMapping("/api/admin/v1/exports/games")
    @RequireRole("ADMIN")
    public Result<?> export(@RequestBody Map<String, Object> body) {
        Map<String, ?> query = body.get("query") instanceof Map<?, ?> map ? stringKeys(map) : Map.of();
        var records = games.export(query, CatalogAdminController.ids(body.get("ids")));
        return Result.success(save("exports", records.size(), records.size(), 0, 0, records));
    }

    @PostMapping("/api/admin/v1/imports/games")
    @RequireRole("ADMIN")
    public Result<?> importGames(@RequestParam MultipartFile file) {
        if (file.isEmpty() || file.getSize() > 10 * 1024 * 1024) invalid("请选择 10 MB 以内的 CSV 或 XLSX 文件");
        List<List<String>> rows = readRows(file);
        if (rows.size() < 2 || rows.size() > 1001) invalid("每次导入 1 至 1000 条游戏");
        List<String> header = rows.get(0).stream().map(value -> value.replace("\uFEFF", "").trim()).toList();
        var errors = new ArrayList<Map<String, Object>>();
        int success = 0, duplicates = 0;
        for (int row = 1; row < rows.size(); row++) {
            List<String> values = rows.get(row);
            var record = new LinkedHashMap<String, Object>();
            for (int column = 0; column < Math.min(header.size(), values.size()); column++)
                record.put(header.get(column), values.get(column));
            try {
                games.save(null, map("name", pick(record, "name", "游戏名称", "名称"), "description", pick(record, "description", "简介"),
                        "company", pick(record, "company", "厂商"), "categories", split(pick(record, "categories", "分类")),
                        "platforms", split(pick(record, "platforms", "平台")), "releaseDate", pick(record, "releaseDate", "发布日期"),
                        "tags", split(pick(record, "tags", "标签")), "cover", pick(record, "cover", "封面")));
                success++;
            } catch (BizException e) {
                if (e.getCode() == 409) duplicates++;
                errors.add(map("row", row + 1, "reason", e.getMessage()));
            }
        }
        return Result.success(save("imports", rows.size() - 1, success, errors.size(), duplicates, errors));
    }

    private List<List<String>> readRows(MultipartFile file) {
        String name = text(file.getOriginalFilename()).toLowerCase(Locale.ROOT);
        try (var stream = file.getInputStream()) {
            if (name.endsWith(".csv")) {
                return CsvUtil.getReader().read(new InputStreamReader(stream, StandardCharsets.UTF_8)).getRows().stream()
                        .map(row -> new ArrayList<>(row.getRawList())).map(row -> (List<String>) row).toList();
            }
            if (name.endsWith(".xlsx")) {
                List<Map<Integer, String>> rows = EasyExcel.read(stream).headRowNumber(0).sheet().doReadSync();
                return rows.stream().map(row -> {
                    int last = row.keySet().stream().mapToInt(Integer::intValue).max().orElse(-1);
                    var cells = new ArrayList<String>();
                    for (int i = 0; i <= last; i++) cells.add(text(row.get(i)));
                    return (List<String>) cells;
                }).toList();
            }
        } catch (RuntimeException | IOException e) {
            throw new BizException("无法读取导入文件，请检查文件格式");
        }
        throw new BizException("仅支持 CSV 或 XLSX");
    }

    private String pick(Map<String, Object> data, String... names) {
        for (String name : names) if (data.containsKey(name)) return text(data, name);
        return "";
    }
    private List<String> split(String value) {
        return value.isBlank() ? List.of() : List.of(value.split("[,，;；|]"));
    }
    private Map<String, Object> stringKeys(Map<?, ?> map) {
        var result = new LinkedHashMap<String, Object>();
        map.forEach((key, value) -> result.put(text(key), value));
        return result;
    }

    private Map<String, Object> save(String kind, int total, int succeeded, int failed, int duplicates, Object payload) {
        String id = UUID.randomUUID().toString();
        String url = "/api/admin/v1/" + kind + "/games/" + id + "/download";
        Map<String, Object> result = map("id",id,"status","COMPLETED","total",total,"succeeded",succeeded,
                "failed",failed,"duplicates",duplicates,"progress",100,kind.equals("imports") ? "reportUrl" : "downloadUrl",url);
        db.jdbc.update("INSERT INTO catalog_transfer(id,user_id,kind,result,payload) VALUES(?,?,?,?,?)",
                id, UserContext.get().getId(), kind, db.json(result), db.json(payload).getBytes(StandardCharsets.UTF_8));
        return result;
    }

    @GetMapping("/api/admin/v1/{kind:imports|exports}/games/{id}")
    @RequireRole("ADMIN")
    public Result<?> transfer(@PathVariable String kind, @PathVariable UUID id) {
        var row = ownedTransfer(kind, id);
        return Result.success(db.readJson(text(row, "result")));
    }

    @GetMapping("/api/admin/v1/{kind:imports|exports}/games/{id}/download")
    @RequireRole("ADMIN")
    public Result<?> download(@PathVariable String kind, @PathVariable UUID id) {
        var row = ownedTransfer(kind, id);
        return Result.success(db.readJson(new String((byte[]) row.get("payload"), StandardCharsets.UTF_8)));
    }

    private Map<String, Object> ownedTransfer(String kind, UUID id) {
        return db.one("SELECT result,payload FROM catalog_transfer WHERE id=? AND kind=? AND user_id=?",
                id.toString(), kind, UserContext.get().getId());
    }
}
