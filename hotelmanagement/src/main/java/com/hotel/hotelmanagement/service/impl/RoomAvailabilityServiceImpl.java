package com.hotel.hotelmanagement.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hotel.hotelmanagement.entity.Order;
import com.hotel.hotelmanagement.entity.Room;
import com.hotel.hotelmanagement.mapper.OrderMapper;
import com.hotel.hotelmanagement.service.RoomAvailabilityService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 房态可用性 Service 实现类
 */
@Service
public class RoomAvailabilityServiceImpl implements RoomAvailabilityService {

    /**
     * 会占用房间的订单状态：1已支付 / 2已入住。
     * 0待支付不占用房间（未付款不锁房，冲突在支付时的房态复核解决）；
     * 3已退房、4已取消 不再占用房间。
     */
    private static final List<Integer> OCCUPYING_STATUS = List.of(1, 2);

    /** 判定「房间有未完结订单」时额外计入的状态：0待支付（未过支付截止时间才计） */
    private static final int STATUS_PENDING_PAYMENT = 0;

    /** 已入住订单在"择优展示占用方"时的权重 */
    private static final int WEIGHT_CHECKED_IN = 2;

    /** 已支付订单在"择优展示占用方"时的权重 */
    private static final int WEIGHT_PAID = 1;

    /**
     * 待支付订单的支付截止时长（分钟）：超时由定时任务自动取消，
     * 与自动取消任务、待支付房间的「未完结」判定共用该配置。
     * 待支付订单不再单独占用房间（见 {@link #OCCUPYING_STATUS}）。
     */
    private final long pendingPaymentMinutes;

    private final OrderMapper orderMapper;

    public RoomAvailabilityServiceImpl(OrderMapper orderMapper,
                                       @Value("${hotel.order.pending-payment-minutes:30}") long pendingPaymentMinutes) {
        this.orderMapper = orderMapper;
        this.pendingPaymentMinutes = pendingPaymentMinutes;
    }

    @Override
    public Set<Long> findOccupiedRoomIds(LocalDate checkInDate, LocalDate checkOutDate, Long excludeOrderId) {
        LocalDate in = resolveCheckIn(checkInDate);
        LocalDate out = resolveCheckOut(in, checkOutDate);
        return findOccupyingOrders(in, out, null, excludeOrderId).stream()
                .map(Order::getRoomId)
                .collect(Collectors.toSet());
    }

    @Override
    public boolean isRoomAvailable(Long roomId, LocalDate checkInDate, LocalDate checkOutDate, Long excludeOrderId) {
        if (roomId == null) {
            return false;
        }
        LocalDate in = resolveCheckIn(checkInDate);
        LocalDate out = resolveCheckOut(in, checkOutDate);
        return findOccupyingOrders(in, out, roomId, excludeOrderId).isEmpty();
    }

    @Override
    public boolean hasUnfinishedOrders(Long roomId) {
        if (roomId == null) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime legacyDeadline = now.minusMinutes(pendingPaymentMinutes);
        return orderMapper.selectCount(new LambdaQueryWrapper<Order>()
                .eq(Order::getRoomId, roomId)
                // 已支付/已入住一律算未完结；待支付订单仅保留未过截止时间的
                .and(w -> w.in(Order::getStatus, OCCUPYING_STATUS)
                        .or(x -> x.eq(Order::getStatus, STATUS_PENDING_PAYMENT)
                                .gt(Order::getPayDeadline, now)
                                .or(y -> y.eq(Order::getStatus, STATUS_PENDING_PAYMENT)
                                        .isNull(Order::getPayDeadline)
                                        .gt(Order::getCreateTime, legacyDeadline))))) > 0;
    }

    @Override
    public List<Room> fillAvailability(List<Room> rooms, LocalDate checkInDate, LocalDate checkOutDate) {
        if (rooms == null || rooms.isEmpty()) {
            return rooms;
        }
        LocalDate in = resolveCheckIn(checkInDate);
        LocalDate out = resolveCheckOut(in, checkOutDate);

        // 一次查出区间内全部占用订单，再按房间归并，避免逐房间查库
        Map<Long, Order> occupierMap = findOccupyingOrders(in, out, null, null).stream()
                .collect(Collectors.toMap(Order::getRoomId, order -> order, this::preferOccupier));

        for (Room room : rooms) {
            // 停用维修的房间直接判定为维修，不参与订单推导
            if (room.getStatus() != null && room.getStatus() == 3) {
                room.setAvailability(REPAIR);
                continue;
            }
            Order occupier = occupierMap.get(room.getId());
            if (occupier == null) {
                room.setAvailability(AVAILABLE);
                continue;
            }
            room.setAvailability(occupier.getStatus() != null && occupier.getStatus() == 2 ? OCCUPIED : BOOKED);
            room.setOccupierOrderId(occupier.getId());
            room.setOccupierOrderNo(occupier.getOrderNo());
        }
        return rooms;
    }

    /**
     * 同一房间存在多笔占用订单时，优先展示"最实"的那笔：已入住 > 已支付 > 待支付
     */
    private Order preferOccupier(Order existing, Order replacement) {
        return statusWeight(existing.getStatus()) >= statusWeight(replacement.getStatus()) ? existing : replacement;
    }

    private int statusWeight(Integer status) {
        if (status == null) {
            return -1;
        }
        if (status == 2) {
            return WEIGHT_CHECKED_IN;
        }
        if (status == 1) {
            return WEIGHT_PAID;
        }
        return 0;
    }

    /**
     * 查询与 [checkInDate, checkOutDate) 相交的占用订单。
     *
     * <p>区间重叠判定：已有订单.入住日 &lt; 本次退房日 且 已有订单.退房日 &gt; 本次入住日。
     * 采用左闭右开区间，因此「上一单 10-07 退房、下一单 10-07 入住」不算冲突。
     *
     * <p>0待支付 不算占用：未付款不锁房，同一房间允许多笔待支付订单并存，
     * 谁先支付谁占用成功；后付者在支付时的房态复核里被拒绝。
     */
    private List<Order> findOccupyingOrders(LocalDate checkInDate, LocalDate checkOutDate,
                                            Long roomId, Long excludeOrderId) {
        return orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .in(Order::getStatus, OCCUPYING_STATUS)
                .lt(Order::getCheckInDate, checkOutDate)
                .gt(Order::getCheckOutDate, checkInDate)
                .eq(roomId != null, Order::getRoomId, roomId)
                .ne(excludeOrderId != null, Order::getId, excludeOrderId));
    }

    /** 入住日期缺省取今天 */
    private LocalDate resolveCheckIn(LocalDate checkInDate) {
        return checkInDate != null ? checkInDate : LocalDate.now();
    }

    /** 退房日期缺省取入住日次日；非法区间（不晚于入住日）时一并兜底 */
    private LocalDate resolveCheckOut(LocalDate checkInDate, LocalDate checkOutDate) {
        if (checkOutDate != null && checkOutDate.isAfter(checkInDate)) {
            return checkOutDate;
        }
        return checkInDate.plusDays(1);
    }
}
