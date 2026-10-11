package com.hotel.hotelmanagement.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.UUID;

/**
 * 基于 Redis 的会话存储（默认实现，{@code hotel.token-store=redis}）。
 *
 * <p>key 设计：{@code hotel:token:admin:{token}} / {@code hotel:token:client:{token}}，
 * value 为用户ID/客户ID 的字符串。两种前缀分池存储，与拦截器
 * "管理端接口查管理端池、/client/** 查客人池"的口径一一对应。
 *
 * <p>TTL 与滑动续期：token 写入时设置 {@code hotel.token.ttl-hours} 的过期时间；
 * 每次有效读取都会把 TTL 重置回初始值。持续使用的账号不会被登出，
 * 闲置超过该时长的 token 自动失效——修复了内存版「token 永不过期」的问题。
 *
 * <p>会话在 Redis 中集中存放：进程重启不再导致全员掉登录态；
 * 多实例部署时任何实例都能校验 token；注销在所有实例同时生效。
 */
@Component
@ConditionalOnProperty(name = "hotel.token-store", havingValue = "redis", matchIfMissing = true)
public class RedisTokenStore implements TokenStore {

    private static final String ADMIN_KEY_PREFIX = "hotel:token:admin:";

    private static final String CLIENT_KEY_PREFIX = "hotel:token:client:";

    /** token 前缀保留（与内存版一致）：M 管理端、C 客人端，便于日志与排查 */
    private static final String ADMIN_TOKEN_PREFIX = "M";

    private static final String CLIENT_TOKEN_PREFIX = "C";

    private final StringRedisTemplate redis;

    /** 会话有效期（小时），写入时设置，读取时重置（滑动续期） */
    private final Duration ttl;

    public RedisTokenStore(StringRedisTemplate redis,
                           @Value("${hotel.token.ttl-hours:24}") long ttlHours) {
        this.redis = redis;
        this.ttl = Duration.ofHours(ttlHours);
    }

    @Override
    public String createToken(Long userId) {
        return create(ADMIN_TOKEN_PREFIX, ADMIN_KEY_PREFIX, userId);
    }

    @Override
    public Long getUserId(String token) {
        return resolve(token, ADMIN_KEY_PREFIX);
    }

    @Override
    public String createClientToken(Long customerId) {
        return create(CLIENT_TOKEN_PREFIX, CLIENT_KEY_PREFIX, customerId);
    }

    @Override
    public Long getClientUserId(String token) {
        return resolve(token, CLIENT_KEY_PREFIX);
    }

    @Override
    public boolean removeAnyToken(String token) {
        if (!StringUtils.hasText(token)) {
            return false;
        }
        Boolean admin = redis.delete(ADMIN_KEY_PREFIX + token);
        Boolean client = redis.delete(CLIENT_KEY_PREFIX + token);
        return Boolean.TRUE.equals(admin) || Boolean.TRUE.equals(client);
    }

    private String create(String tokenPrefix, String keyPrefix, Long id) {
        String token = tokenPrefix + UUID.randomUUID().toString().replace("-", "");
        redis.opsForValue().set(keyPrefix + token, String.valueOf(id), ttl);
        return token;
    }

    /**
     * 读会话并滑动续期。GET 与 EXPIRE 两步非原子：
     * 极端情况下 token 恰好在两步之间过期，EXPIRE 对不存在的 key 返回 false，结果仍是"未登录"，安全。
     */
    private Long resolve(String token, String keyPrefix) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        String key = keyPrefix + token;
        String value = redis.opsForValue().get(key);
        if (value == null) {
            return null;
        }
        redis.expire(key, ttl);
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            // 库里出现非数字脏数据时按无效 token 处理，不让异常打断请求
            return null;
        }
    }
}
