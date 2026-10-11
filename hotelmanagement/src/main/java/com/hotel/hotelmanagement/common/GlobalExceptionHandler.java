package com.hotel.hotelmanagement.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

/**
 * 全局异常处理器。
 *
 * <p>状态码约定：业务错误返回 HTTP 200 + 响应体 code（前端按 code 分支）；
 * 认证/授权失败由拦截器直接返回真实的 401/403。
 *
 * <p>安全约定：<b>未预期的异常只把堆栈写进服务端日志，对外返回统一提示</b>。
 * 改造前把 {@code e.getMessage()} 直接拼进响应，会把 SQL 语句、表名、
 * 类名等内部信息泄露给客户端。
 *
 * <p>可观测性约定：所有异常都会落日志，未预期异常带完整堆栈，
 * 因此排查问题不再需要靠控制台里滚动的 SQL 输出。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常：可预期，按 warn 记录，不打堆栈（日志里保留 code 与提示，便于对账）
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    /**
     * 请求体参数校验失败（@RequestBody + @Valid）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidException(MethodArgumentNotValidException e) {
        String message = firstFieldMessage(e.getBindingResult().getFieldError());
        log.warn("参数校验失败: {}", message);
        return Result.error(ResultCode.BAD_REQUEST, message);
    }

    /**
     * 表单/查询参数绑定校验失败（@ModelAttribute + @Valid）
     */
    @ExceptionHandler(BindException.class)
    public Result<Void> handleBindException(BindException e) {
        String message = firstFieldMessage(e.getBindingResult().getFieldError());
        log.warn("参数绑定失败: {}", message);
        return Result.error(ResultCode.BAD_REQUEST, message);
    }

    /**
     * 请求体不是合法 JSON
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败: {}", e.getMessage());
        return Result.error(ResultCode.BAD_REQUEST, "请求参数格式不正确");
    }

    /**
     * 缺少必填请求参数
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingParam(MissingServletRequestParameterException e) {
        log.warn("缺少请求参数: {}", e.getParameterName());
        return Result.error(ResultCode.BAD_REQUEST, "缺少必要参数：" + e.getParameterName());
    }

    /**
     * 参数类型不匹配（例如路径需要 Long 却传了字母）
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("参数类型不匹配: name={}, value={}", e.getName(), e.getValue());
        return Result.error(ResultCode.BAD_REQUEST, "参数格式不正确：" + e.getName());
    }

    /**
     * 请求方法不支持
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Result<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("请求方法不支持: {}", e.getMessage());
        return Result.error(ResultCode.BAD_REQUEST, "请求方法不支持");
    }

    /**
     * 唯一键冲突（手机号重复、订单号重复等）
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public Result<Void> handleDuplicateKey(DuplicateKeyException e) {
        log.warn("唯一键冲突: {}", e.getMostSpecificCause().getMessage());
        return Result.error(ResultCode.BAD_REQUEST, "数据已存在，请勿重复提交");
    }

    /**
     * 请求的静态资源或接口路径不存在。
     *
     * <p>必须显式处理：Spring 6.1 起找不到静态资源会抛 {@link NoResourceFoundException}，
     * 如果不单独接住，就会被最下面的兜底 Exception 处理器抢走，
     * 把本该是 404 的响应变成 500"系统繁忙"——既误导前端排查，也让 404 语义丢失。
     *
     * <p>这是 HTTP 层面的错误，因此返回真实的 404 状态码（与拦截器的 401/403 一致），
     * 而不是像业务错误那样包成 HTTP 200。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> handleNoResource(NoResourceFoundException e) {
        log.warn("请求的资源不存在: {}", e.getResourcePath());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(ResultCode.NOT_FOUND, "请求的资源不存在"));
    }

    /**
     * 兜底：完整堆栈只进日志，对外只给通用提示
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.error(ResultCode.SERVER_ERROR, "系统繁忙，请稍后重试");
    }

    private String firstFieldMessage(FieldError fieldError) {
        return fieldError != null ? fieldError.getDefaultMessage() : "参数校验失败";
    }
}
