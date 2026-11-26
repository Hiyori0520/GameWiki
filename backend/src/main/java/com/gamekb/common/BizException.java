package com.gamekb.common;

/**
 * 业务异常。Service 层直接抛出，由 GlobalExceptionHandler 统一转成 Result。
 *
 * <pre>
 * throw new BizException("该用户名已被注册");
 * throw new BizException(ErrorCode.FORBIDDEN);
 * </pre>
 */
public class BizException extends RuntimeException {

    private final int code;

    public BizException(String message) {
        this(ErrorCode.PARAM_ERROR.getCode(), message);
    }

    public BizException(ErrorCode errorCode) {
        this(errorCode.getCode(), errorCode.getMessage());
    }

    public BizException(ErrorCode errorCode, String message) {
        this(errorCode.getCode(), message);
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
