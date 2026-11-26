package com.gamekb.controller;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gamekb.auth.AuthConst;
import com.gamekb.auth.UserContext;
import com.gamekb.common.BizException;
import com.gamekb.common.ErrorCode;
import com.gamekb.common.Result;
import com.gamekb.dto.ChangePasswordRequest;
import com.gamekb.dto.LoginRequest;
import com.gamekb.dto.RegisterRequest;
import com.gamekb.entity.SysUser;
import com.gamekb.mapper.SysUserMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 用户认证：登录、注册、登出、当前用户、修改密码。
 * 契约见 docs/API契约.md 第五节。
 *
 * 注意参数校验写在 dto 上（@NotBlank/@Size），校验失败由 GlobalExceptionHandler 统一返回 400，
 * 这里不再手写 if 判空。
 */
@Tag(name = "用户认证管理", description = "登录、注册、登出及个人信息接口")
@RestController
@RequestMapping("/api/user")
public class UserController {

    @Resource
    private SysUserMapper sysUserMapper;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private BCryptPasswordEncoder passwordEncoder;
    @Resource
    private com.gamekb.service.CatalogUserService catalogUsers;

    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@Valid @RequestBody LoginRequest request) {
        String username = request.getUsername().trim();

        SysUser user = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));

        // 账号不存在与密码错误返回同一句提示，避免被枚举账号
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "账号或密码错误");
        }

        if (Integer.valueOf(2).equals(user.getStatus())) {
            catalogUsers.restoreExpiredBan(user.getId());
            user = sysUserMapper.selectById(user.getId());
        }
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) {
            throw new BizException(ErrorCode.FORBIDDEN, "该账号已被禁用，请联系管理员");
        }

        catalogUsers.recordLogin(user.getId());
        String token = IdUtil.fastSimpleUUID();
        user.setPassword(null);
        redisTemplate.opsForValue().set(
                AuthConst.tokenKey(token), user,
                AuthConst.TOKEN_TTL_HOURS, AuthConst.TOKEN_TTL_UNIT);

        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("userInfo", user);
        return Result.success(data);
    }

    @Operation(summary = "新用户注册")
    @PostMapping("/register")
    public Result<String> register(@Valid @RequestBody RegisterRequest request) {
        String username = request.getUsername().trim();

        Long exists = sysUserMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
        if (exists != null && exists > 0) {
            throw new BizException("该用户名已被注册");
        }

        SysUser entity = new SysUser();
        entity.setUsername(username);
        // 只存 BCrypt 密文
        entity.setPassword(passwordEncoder.encode(request.getPassword()));
        String nickname = request.getNickname() == null ? null : request.getNickname().trim();
        entity.setNickname(nickname == null || nickname.isEmpty() ? "新兵_" + username : nickname);
        entity.setRole("PLAYER");
        entity.setStatus(1);
        sysUserMapper.insert(entity);

        return Result.success("注册成功，请登录", null);
    }

    @Operation(summary = "退出登录")
    @PostMapping("/logout")
    public Result<String> logout(HttpServletRequest request) {
        String token = request.getHeader(AuthConst.HEADER);
        if (token != null && !token.isBlank()) {
            token = token.trim();
            if (token.startsWith("Bearer ")) {
                token = token.substring(7).trim();
            }
            redisTemplate.delete(AuthConst.tokenKey(token));
        }
        return Result.success("已退出登录", null);
    }

    @Operation(summary = "获取当前登录人信息")
    @GetMapping("/info")
    public Result<SysUser> info() {
        SysUser current = UserContext.get();
        if (current == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        current.setPassword(null);
        return Result.success(current);
    }

    @Operation(summary = "修改密码")
    @PutMapping("/password")
    public Result<String> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        SysUser dbUser = sysUserMapper.selectById(UserContext.requireUserId());
        if (dbUser == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "用户不存在，请重新登录");
        }
        if (!passwordEncoder.matches(request.getOldPassword(), dbUser.getPassword())) {
            throw new BizException("原密码不正确");
        }

        SysUser update = new SysUser();
        update.setId(dbUser.getId());
        update.setPassword(passwordEncoder.encode(request.getNewPassword()));
        sysUserMapper.updateById(update);

        return Result.success("密码修改成功", null);
    }
}
