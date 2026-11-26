package com.gamekb.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gamekb.common.Result;
import com.gamekb.entity.SysUser;
import com.gamekb.mapper.SysUserMapper;
import com.gamekb.service.CatalogUserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Arrays;

/**
 * 登录与角色校验拦截器。
 *
 * 校验顺序：OPTIONS 放行 → 取 token → 查 Redis → 校验账号状态 → 写 UserContext → 校验注解角色。
 * 校验通过后自动为 token 续期，避免用户操作中途被踢。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    @Resource
    private SysUserMapper sysUserMapper;
    @Resource
    private CatalogUserService catalogUsers;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {

        // 跨域预检请求直接放行，否则浏览器拿不到 CORS 头
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }

        // 非 Controller 方法（静态资源、错误页等）不处理
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        String token = request.getHeader(AuthConst.HEADER);
        if (token == null || token.isBlank()) {
            return reject(response, 401, "未登录，请先登录");
        }
        token = token.trim();
        // 兼容前端误加 Bearer 前缀的情况
        if (token.startsWith("Bearer ")) {
            token = token.substring(7).trim();
        }

        String key = AuthConst.tokenKey(token);
        Object cached = redisTemplate.opsForValue().get(key);
        if (!(cached instanceof SysUser cachedUser)) {
            return reject(response, 401, "登录已过期，请重新登录");
        }

        SysUser user = sysUserMapper.selectById(cachedUser.getId());
        if (user == null) {
            redisTemplate.delete(key);
            return reject(response, 401, "账号不存在");
        }
        if (Integer.valueOf(2).equals(user.getStatus())) {
            catalogUsers.restoreExpiredBan(user.getId());
            user = sysUserMapper.selectById(user.getId());
        }
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) {
            redisTemplate.delete(key);
            return reject(response, 403, "该账号已被禁用，请联系管理员");
        }

        // 校验角色：方法上的注解优先于类上的
        RequireRole requireRole = handlerMethod.getMethodAnnotation(RequireRole.class);
        if (requireRole == null) {
            requireRole = handlerMethod.getBeanType().getAnnotation(RequireRole.class);
        }
        if (requireRole != null) {
            String role = user.getRole();
            boolean allowed = Arrays.asList(requireRole.value()).contains(role);
            if (!allowed) {
                return reject(response, 403, "无访问权限");
            }
        }

        // 活跃即续期
        redisTemplate.expire(key, AuthConst.TOKEN_TTL_HOURS, AuthConst.TOKEN_TTL_UNIT);
        user.setPassword(null);
        UserContext.set(user);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // 必须清理，否则 Tomcat 线程复用会把上一个用户的信息带到下一个请求
        UserContext.clear();
    }

    private boolean reject(HttpServletResponse response, int code, String message) throws IOException {
        // HTTP 状态保持 200，业务码放 body，与 docs/API契约.md 一致
        response.setStatus(HttpServletResponse.SC_OK);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(MAPPER.writeValueAsString(Result.error(code, message)));
        return false;
    }
}
