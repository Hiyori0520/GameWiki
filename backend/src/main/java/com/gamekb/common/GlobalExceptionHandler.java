package com.gamekb.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.List;

/**
 * 全局异常处理。所有异常统一转成 {@link Result}，前端只需判断 code。
 *
 * 有了它，Controller / Service 里不用再写一堆 if 判断返回 Result.error，
 * 业务不满足直接 throw new BizException("xxx") 即可。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常：可预期，按业务码原样返回，不打错误日志 */
    @ExceptionHandler(BizException.class)
    public Result<Void> handleBiz(BizException e) {
        return Result.error(e.getCode(), e.getMessage());
    }

    /** 请求体 @Valid 校验失败（@RequestBody + @Valid） */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleInvalidBody(MethodArgumentNotValidException e) {
        return Result.error(ErrorCode.PARAM_ERROR.getCode(), firstFieldMessage(e.getBindingResult().getFieldErrors()));
    }

    /** 表单/查询参数绑定失败 */
    @ExceptionHandler(BindException.class)
    public Result<Void> handleBind(BindException e) {
        return Result.error(ErrorCode.PARAM_ERROR.getCode(), firstFieldMessage(e.getBindingResult().getFieldErrors()));
    }

    /** 方法参数上的 @Validated 校验失败 */
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraint(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .findFirst()
                .map(ConstraintViolation::getMessage)
                .orElse(ErrorCode.PARAM_ERROR.getMessage());
        return Result.error(ErrorCode.PARAM_ERROR.getCode(), message);
    }

    /** 缺少必填的 query 参数 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingParam(MissingServletRequestParameterException e) {
        return Result.error(ErrorCode.PARAM_ERROR.getCode(), "缺少必要参数：" + e.getParameterName());
    }

    /** 参数类型不匹配，例如 /api/guide/abc 传了非数字 id */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return Result.error(ErrorCode.PARAM_ERROR.getCode(), "参数格式不正确：" + e.getName());
    }

    /** 请求体不是合法 JSON */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleUnreadable(HttpMessageNotReadableException e) {
        return Result.error(ErrorCode.PARAM_ERROR.getCode(), "请求体格式错误，请检查 JSON 是否合法");
    }

    /** 请求方法不支持，例如用 GET 调了只支持 POST 的接口 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Result<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return Result.error(ErrorCode.PARAM_ERROR.getCode(), "不支持的请求方法：" + e.getMethod());
    }

    /** 静态资源或接口路径不存在 */
    @ExceptionHandler(NoResourceFoundException.class)
    public Result<Void> handleNoResource(NoResourceFoundException e) {
        return Result.error(ErrorCode.NOT_FOUND.getCode(), "接口不存在：" + e.getResourcePath());
    }

    /**
     * Redis 连不上或超时。这是本地开发最常见的问题（忘记启动 Redis），
     * 单独拦一下给出明确提示，否则只会看到"系统繁忙"白排查半小时。
     */
    @ExceptionHandler({DataAccessResourceFailureException.class, QueryTimeoutException.class})
    public Result<Void> handleRedisDown(Exception e) {
        log.error("Redis 连接失败，请确认服务已启动", e);
        return Result.error(ErrorCode.SERVER_ERROR.getCode(),
                "缓存服务未连接，请先启动 Redis（默认端口 6379）后重试");
    }

    /** 兜底：未预料的异常，必须打日志，但不要把堆栈暴露给前端 */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleUnknown(Exception e) {
        log.error("未处理的系统异常", e);
        return Result.error(ErrorCode.SERVER_ERROR.getCode(), ErrorCode.SERVER_ERROR.getMessage());
    }

    private String firstFieldMessage(List<FieldError> errors) {
        if (errors == null || errors.isEmpty()) {
            return ErrorCode.PARAM_ERROR.getMessage();
        }
        FieldError first = errors.get(0);
        return first.getDefaultMessage() == null ? "参数错误：" + first.getField() : first.getDefaultMessage();
    }
}
