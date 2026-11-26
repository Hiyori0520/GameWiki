package com.gamekb.common;

import lombok.Data;

/**
 * 统一响应结构，见 docs/API契约.md
 * code: 200 成功 / 400 参数错误 / 401 未登录 / 403 无权限 / 500 服务端异常
 */
@Data
public class Result<T> {

    private Integer code;
    private String message;
    private T data;

    public static <T> Result<T> success(T data) {
        return build(200, "success", data);
    }

    public static <T> Result<T> success(String message, T data) {
        return build(200, message, data);
    }

    public static <T> Result<T> error(String message) {
        return build(500, message, null);
    }

    public static <T> Result<T> error(Integer code, String message) {
        return build(code, message, null);
    }

    private static <T> Result<T> build(Integer code, String message, T data) {
        Result<T> r = new Result<>();
        r.setCode(code);
        r.setMessage(message);
        r.setData(data);
        return r;
    }
}
