package com.hotel.hotelmanagement.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hotel.hotelmanagement.entity.Room;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 房间表 Mapper 接口
 */
public interface RoomMapper extends BaseMapper<Room> {

    /**
     * 按主键加行锁查询房间（必须在事务内调用）。
     *
     * <p>房间的日期区间占用无法用数据库唯一键约束（区间重叠不违反唯一性），
     * 因此只能靠行锁把同一房间的并发下单/支付串行化。
     * 锁的粒度是房间行，不同房间之间互不阻塞。
     */
    @Select("SELECT id, room_number, type, price, status, floor, description "
            + "FROM t_room WHERE id = #{id} FOR UPDATE")
    Room selectByIdForUpdate(@Param("id") Long id);
}
