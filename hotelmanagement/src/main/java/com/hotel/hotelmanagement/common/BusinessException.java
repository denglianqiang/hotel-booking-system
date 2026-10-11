package com.hotel.hotelmanagement.common;

/**
 * 自定义业务异常：表示"请求本身合法，但不满足业务规则"。
 *
 * <p>默认响应码为 {@link ResultCode#BAD_REQUEST}（400）——
 * 这类提示是给用户看的、可以通过修改操作来纠正的错误，
 * 改造前默认 500 把它当成了服务器故障。
 * 登录态/权限类问题请显式传 401 / 403。
 */
public class BusinessException extends RuntimeException {

    /** 业务状态码 */
    private final Integer code;

    public BusinessException(String message) {
        super(message);
        this.code = ResultCode.BAD_REQUEST;
    }

    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    public Integer getCode() {
        return code;
    }
}
