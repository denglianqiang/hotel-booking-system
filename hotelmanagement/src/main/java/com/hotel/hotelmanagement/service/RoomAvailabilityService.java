package com.hotel.hotelmanagement.service;

import com.hotel.hotelmanagement.entity.Room;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * 房态可用性服务：房态由「订单 + 日期区间」推导的<b>唯一出口</b>。
 *
 * <p>设计要点：房间的占用不是房间自身的属性，而是「订单 + 日期区间」的函数。
 * 因此 t_room.status 只保留可售性（0正常销售 / 3停用维修），
 * 已订/在住一律由本服务推导，任何业务代码都不得另写一套区间重叠判断，
 * 否则各处的口径会再次漂移（这正是之前超卖问题的根因）。
 */
public interface RoomAvailabilityService {

    /** 派生占用状态：可订 */
    String AVAILABLE = "available";

    /** 派生占用状态：已订（区间内有未超时待支付或已支付订单） */
    String BOOKED = "booked";

    /** 派生占用状态：在住（区间内有已入住订单） */
    String OCCUPIED = "occupied";

    /** 派生占用状态：维修停用 */
    String REPAIR = "repair";

    /**
     * 查询指定日期区间内被占用的房间ID集合
     *
     * @param excludeOrderId 需要排除的订单ID（修改/支付自身订单时传自身，避免自己和自己冲突）
     */
    Set<Long> findOccupiedRoomIds(LocalDate checkInDate, LocalDate checkOutDate, Long excludeOrderId);

    /**
     * 判断某房间在指定区间是否可订
     */
    boolean isRoomAvailable(Long roomId, LocalDate checkInDate, LocalDate checkOutDate, Long excludeOrderId);

    /**
     * 房间是否存在未完结订单（未超时待支付 / 已支付 / 已入住）。
     * 用于「删除房间」「变更房间状态」的前置校验，与占用判定共用同一套口径。
     */
    boolean hasUnfinishedOrders(Long roomId);

    /**
     * 为房间列表填充派生占用状态（就地填充并原样返回）。
     * 一次查询批量填充，避免逐个房间查库。
     */
    List<Room> fillAvailability(List<Room> rooms, LocalDate checkInDate, LocalDate checkOutDate);
}
