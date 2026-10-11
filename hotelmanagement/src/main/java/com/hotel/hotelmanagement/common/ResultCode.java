package com.hotel.hotelmanagement.common;

/**
 * 统一响应码。
 *
 * <p>状态码约定（整个后端保持一致）：
 * <ul>
 *   <li><b>认证 / 授权失败返回真实的 HTTP 状态码</b>（401 / 403），由拦截器直接写出；</li>
 *   <li><b>业务错误一律 HTTP 200 + 响应体里的 code</b>，前端只需按 code 分支处理。</li>
 * </ul>
 */
public final class ResultCode {

    /** 成功 */
    public static final int SUCCESS = 200;

    /** 参数校验失败或业务规则不满足（客户端可纠正） */
    public static final int BAD_REQUEST = 400;

    /** 未登录或登录已过期 */
    public static final int UNAUTHORIZED = 401;

    /** 已登录但没有该功能的权限 */
    public static final int FORBIDDEN = 403;

    /** 请求的资源不存在（同时返回真实的 HTTP 404） */
    public static final int NOT_FOUND = 404;

    /** 服务端异常（不对客户端暴露细节） */
    public static final int SERVER_ERROR = 500;

    private ResultCode() {
    }
}
