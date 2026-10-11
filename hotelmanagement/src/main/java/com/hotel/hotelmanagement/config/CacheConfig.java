package com.hotel.hotelmanagement.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 读路径缓存配置（Spring Cache + Redis）。
 *
 * <p><b>缓存的边界（设计约定）</b>：只缓存「展示读路径」——
 * 管理端房态图（{@code roomStatus}）、客人端可订房间列表（{@code bookableRooms}）、
 * 统计看板（{@code dashboard}）。
 * 下单、支付、入住里的占用校验（{@code RoomAvailabilityService.isRoomAvailable}）
 * 永远直查数据库并配合行锁，<b>绝不读缓存</b>。因此缓存的最坏后果是
 * "页面显示可订、下单被告知冲突"，绝不会造成超卖。
 *
 * <p><b>一致性策略</b>：订单与房间的每一次写操作（创建/支付/入住/退房/取消/
 * 超时自动取消、房间的增删改）都 {@code @CacheEvict(allEntries=true)} 清空相关缓存；
 * TTL 只是兜底。驱逐发生在事务提交之前，理论上存在
 * "驱逐后、提交前另一请求把旧值重新载入"的微小窗口，由短 TTL（30 秒）封顶——
 * 这是读多写少场景下简单性与严格一致性的取舍。
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /** 管理端房态图（/room/status） */
    public static final String ROOM_STATUS = "roomStatus";

    /** 客人端可订房间列表（/client/rooms，不含维修房，与管理端结果不同，单独一个缓存） */
    public static final String BOOKABLE_ROOMS = "bookableRooms";

    /** 统计看板（/statistics/dashboard） */
    public static final String DASHBOARD = "dashboard";

    /** 所有「随订单/房间变动」的缓存，写操作统一按这一组清空 */
    public static final String[] WRITE_SENSITIVE_CACHES = {ROOM_STATUS, BOOKABLE_ROOMS, DASHBOARD};

    /**
     * Redis 缓存管理器（默认）：JSON 序列化 + 按缓存名分别设置 TTL。
     */
    @Bean
    @ConditionalOnProperty(name = "hotel.cache-type", havingValue = "redis", matchIfMissing = true)
    public CacheManager redisCacheManager(
            RedisConnectionFactory factory,
            @Value("${hotel.cache.dashboard-ttl-seconds:60}") long dashboardTtlSeconds,
            @Value("${hotel.cache.availability-ttl-seconds:30}") long availabilityTtlSeconds) {

        RedisCacheConfiguration defaults = baseConfig()
                .entryTtl(Duration.ofSeconds(availabilityTtlSeconds));

        Map<String, RedisCacheConfiguration> perCache = new LinkedHashMap<>();
        perCache.put(ROOM_STATUS, baseConfig().entryTtl(Duration.ofSeconds(availabilityTtlSeconds)));
        perCache.put(BOOKABLE_ROOMS, baseConfig().entryTtl(Duration.ofSeconds(availabilityTtlSeconds)));
        perCache.put(DASHBOARD, baseConfig().entryTtl(Duration.ofSeconds(dashboardTtlSeconds)));

        return RedisCacheManager.builder(factory)
                .cacheDefaults(defaults)
                .withInitialCacheConfigurations(perCache)
                .build();
    }

    /**
     * 进程内缓存（{@code hotel.cache-type=memory}）：免 Redis 场景（如集成测试）。
     * 语义与 Redis 版一致，只是没有 TTL（测试中每个用例前会手动清缓存）。
     */
    @Bean
    @ConditionalOnProperty(name = "hotel.cache-type", havingValue = "memory")
    public CacheManager memoryCacheManager() {
        return new ConcurrentMapCacheManager(WRITE_SENSITIVE_CACHES);
    }

    /**
     * key 用可读字符串（如 {@code roomStatus::2026-10-05|2026-10-06}），
     * value 用带类型信息的 JSON（GenericJackson2JsonRedisSerializer），
     * 反序列化后 Map/List/BigDecimal 的实际类型得以保留。
     */
    private RedisCacheConfiguration baseConfig() {
        return RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(SerializationPair.fromSerializer(jsonSerializer()))
                .disableCachingNullValues();
    }

    private GenericJackson2JsonRedisSerializer jsonSerializer() {
        ObjectMapper mapper = new ObjectMapper();
        // 实体里目前没有 java.time 字段，注册 jsr310 是为后续演进兜底
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // 写入 @class 类型信息，Map/实体反序列化后才能还原为原本类型
        mapper.activateDefaultTyping(LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        return new GenericJackson2JsonRedisSerializer(mapper);
    }
}
