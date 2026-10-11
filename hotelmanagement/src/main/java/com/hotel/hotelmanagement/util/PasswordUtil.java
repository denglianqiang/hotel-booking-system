package com.hotel.hotelmanagement.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 密码工具类。
 *
 * <p>新密码统一使用 <b>BCrypt</b> 存储：自带随机盐、可调计算强度，
 * 同一个明文每次得到的摘要都不同，也就不存在"彩虹表/一秒反查"的问题
 * （改造前用的是 32 位无盐 MD5，演示账号的密码可以被直接反查出来）。
 *
 * <p>历史数据里存的是无盐 MD5 摘要。为了不让存量账号直接失效，
 * {@link #matches} 仍然能够校验 MD5；调用方在登录成功后用
 * {@link #isLegacy} 判断并把它透明升级成 BCrypt，实现平滑迁移。
 */
public class PasswordUtil {

    /** 历史无盐 MD5 摘要的长度 */
    private static final int MD5_HEX_LENGTH = 32;

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    private PasswordUtil() {
    }

    /**
     * 生成 BCrypt 摘要（已含随机盐，同一明文每次结果不同）
     */
    public static String encode(String rawPassword) {
        return ENCODER.encode(rawPassword);
    }

    /**
     * 校验明文密码，同时兼容 BCrypt 与历史 MD5 摘要
     */
    public static boolean matches(String rawPassword, String storedPassword) {
        if (rawPassword == null || storedPassword == null || storedPassword.isEmpty()) {
            return false;
        }
        if (isLegacy(storedPassword)) {
            return Md5Util.encode(rawPassword).equalsIgnoreCase(storedPassword);
        }
        return ENCODER.matches(rawPassword, storedPassword);
    }

    /**
     * 判断存储的摘要是否为历史无盐 MD5（32 位十六进制）
     */
    public static boolean isLegacy(String storedPassword) {
        return storedPassword != null
                && storedPassword.length() == MD5_HEX_LENGTH
                && storedPassword.matches("[0-9a-fA-F]{32}");
    }
}
