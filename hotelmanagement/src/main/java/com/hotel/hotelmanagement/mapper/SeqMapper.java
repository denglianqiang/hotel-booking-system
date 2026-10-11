package com.hotel.hotelmanagement.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 序号表 Mapper。
 *
 * <p>订单号形如 {@code ORD + yyyyMMdd + 3位当日序号}（如 ORD20261004001）。
 * "当日序号"必须并发安全：改造前用的是 {@code "ORD" + System.currentTimeMillis()}，
 * 同一毫秒内的两笔下单会撞唯一键直接报 500；而"先 count 再 +1"同样会并发重复。
 *
 * <p>这里用一条原子语句完成"取下一个值"：
 * {@code ON DUPLICATE KEY UPDATE next_val = LAST_INSERT_ID(next_val + 1)}
 * 会把本次自增结果写入连接级的 LAST_INSERT_ID()，再读回来即可。
 * 插入分支用 {@code LAST_INSERT_ID(1)} 让首次取值同样走这条路径。
 */
public interface SeqMapper {

    /**
     * 取下一个序号（原子操作，返回自增后的值由 {@link #lastValue()} 读取）
     */
    @Insert("INSERT INTO t_seq(name, next_val) VALUES(#{name}, LAST_INSERT_ID(1)) "
            + "ON DUPLICATE KEY UPDATE next_val = LAST_INSERT_ID(next_val + 1)")
    int bump(@Param("name") String name);

    /** 读取当前连接上最近一次的自增值 */
    @Select("SELECT LAST_INSERT_ID()")
    long lastValue();
}
