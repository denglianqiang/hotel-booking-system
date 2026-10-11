package com.hotel.hotelmanagement.util;

/**
 * 登录会话存储接口。
 *
 * <p>管理端账号与普通用户共用统一的登录入口（/user/login），但 token 分两个池管理：
 * 管理端接口校验管理端 token，/client/** 接口校验客人 token，注销则两侧都清理。
 *
 * <p>两个实现按配置 {@code hotel.token-store} 二选一：
 * <ul>
 *   <li>{@link RedisTokenStore}（默认）：token 存 Redis，带 TTL 与滑动续期，
 *       进程重启不丢登录态，多实例部署可用；</li>
 *   <li>{@link InMemoryTokenStore}：进程内 Map（保留改造前的行为），供
 *       无 Redis 环境（如跑集成测试）使用。</li>
 * </ul>
 */
public interface TokenStore {

    /**
     * 生成管理端 token（带 M 前缀）并绑定用户ID
     */
    String createToken(Long userId);

    /**
     * 根据管理端 token 获取用户ID，无效返回 null。
     * 有效 token 会触发滑动续期（TTL 重置）。
     */
    Long getUserId(String token);

    /**
     * 生成客人端 token（带 C 前缀）并绑定客户ID
     */
    String createClientToken(Long customerId);

    /**
     * 根据客人端 token 获取客户ID，无效返回 null。
     * 有效 token 会触发滑动续期（TTL 重置）。
     */
    Long getClientUserId(String token);

    /**
     * 注销任意 token（不区分管理端/客人端）。
     *
     * <p>登录入口是统一的（/user/login 同时支持管理端账号与普通用户手机号），
     * 注销也应当统一：调用方不必先判断 token 属于哪一端，避免
     * "普通用户退出登录调了管理端注销接口、token 实际没失效"这类问题。
     *
     * @return 是否确实注销了一个有效 token
     */
    boolean removeAnyToken(String token);
}
