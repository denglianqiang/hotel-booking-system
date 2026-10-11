package com.hotel.hotelmanagement.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内会话存储（兜底实现，{@code hotel.token-store=memory}）。
 *
 * <p>即原「课程设计简化方案」：token 存 ConcurrentHashMap，进程重启后全部失效。
 * 保留它是为了本机没装 Redis、或跑集成测试时系统仍能完整运行；
 * 生产/演示一律用 {@link RedisTokenStore}。
 *
 * <p>与 Redis 版保持同样的 TTL + 滑动续期语义（读取时惰性检查过期并顺延），
 * 避免两种实现行为不一致导致"测试通过、上线不一样"。
 */
@Component
@ConditionalOnProperty(name = "hotel.token-store", havingValue = "memory")
public class InMemoryTokenStore implements TokenStore {

    private static final String ADMIN_TOKEN_PREFIX = "M";

    private static final String CLIENT_TOKEN_PREFIX = "C";

    /** 会话有效期（毫秒） */
    private final long ttlMillis;

    /** 管理端 token -> 会话条目 */
    private final Map<String, Entry> tokenMap = new ConcurrentHashMap<>();

    /** 客人 token -> 会话条目 */
    private final Map<String, Entry> clientTokenMap = new ConcurrentHashMap<>();

    public InMemoryTokenStore(@Value("${hotel.token.ttl-hours:24}") long ttlHours) {
        this.ttlMillis = ttlHours * 3600_000L;
    }

    @Override
    public String createToken(Long userId) {
        return create(ADMIN_TOKEN_PREFIX, tokenMap, userId);
    }

    @Override
    public Long getUserId(String token) {
        return resolve(token, tokenMap);
    }

    @Override
    public String createClientToken(Long customerId) {
        return create(CLIENT_TOKEN_PREFIX, clientTokenMap, customerId);
    }

    @Override
    public Long getClientUserId(String token) {
        return resolve(token, clientTokenMap);
    }

    @Override
    public boolean removeAnyToken(String token) {
        if (!StringUtils.hasText(token)) {
            return false;
        }
        boolean removed = tokenMap.remove(token) != null;
        removed |= clientTokenMap.remove(token) != null;
        return removed;
    }

    private String create(String tokenPrefix, Map<String, Entry> map, Long id) {
        String token = tokenPrefix + UUID.randomUUID().toString().replace("-", "");
        map.put(token, new Entry(id, System.currentTimeMillis() + ttlMillis));
        return token;
    }

    /** 惰性过期 + 滑动续期：过期条目顺手清掉，有效条目把到期时间顺延 */
    private Long resolve(String token, Map<String, Entry> map) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        Entry entry = map.get(token);
        if (entry == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        if (entry.expiresAt <= now) {
            map.remove(token);
            return null;
        }
        map.put(token, new Entry(entry.id, now + ttlMillis));
        return entry.id;
    }

    /** 会话条目：绑定的用户/客户ID + 到期时间戳 */
    private record Entry(Long id, long expiresAt) {
    }
}
