package com.gamekb.auth;

import java.util.concurrent.TimeUnit;

/**
 * 认证相关常量，登录、登出、拦截器共用，避免各写一份对不上。
 */
public final class AuthConst {

    private AuthConst() {
    }

    /** 请求头名称 */
    public static final String HEADER = "Authorization";

    /** Redis 中 token 的键前缀，完整键：kb:user:token:{token} */
    public static final String TOKEN_PREFIX = "kb:user:token:";

    /** token 有效期（小时） */
    public static final int TOKEN_TTL_HOURS = 12;

    public static final TimeUnit TOKEN_TTL_UNIT = TimeUnit.HOURS;

    public static String tokenKey(String token) {
        return TOKEN_PREFIX + token;
    }
}
