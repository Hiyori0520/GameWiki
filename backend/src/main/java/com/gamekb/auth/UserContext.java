package com.gamekb.auth;

import com.gamekb.entity.SysUser;

/**
 * 当前登录人上下文。
 * 由 AuthInterceptor 在 preHandle 中写入，afterCompletion 中清理，
 * 避免线程池复用导致用户信息串号。
 */
public final class UserContext {

    private static final ThreadLocal<SysUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(SysUser user) {
        HOLDER.set(user);
    }

    public static SysUser get() {
        return HOLDER.get();
    }

    /** 取当前登录人 ID，未登录时直接抛异常（正常情况下拦截器已保证不会发生） */
    public static Long requireUserId() {
        SysUser user = HOLDER.get();
        if (user == null || user.getId() == null) {
            throw new IllegalStateException("未获取到登录用户，请检查拦截器配置");
        }
        return user.getId();
    }

    public static boolean isAdmin() {
        SysUser user = HOLDER.get();
        return user != null && "ADMIN".equals(user.getRole());
    }

    public static void clear() {
        HOLDER.remove();
    }
}
