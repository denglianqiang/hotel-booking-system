package com.hotel.hotelmanagement.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * MD5 工具类。
 *
 * <p><b>已废弃，仅用于兼容历史数据</b>：老版本用无盐 MD5 存密码，
 * 现在新密码一律用 {@link PasswordUtil}（BCrypt）生成。
 * 保留本类只为让存量摘要仍能校验通过，并在登录成功后透明升级。
 */
public class Md5Util {

    /**
     * 计算字符串的 MD5 摘要（32 位小写十六进制）
     */
    public static String encode(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 算法不可用", e);
        }
    }
}
