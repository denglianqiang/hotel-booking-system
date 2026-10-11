package com.hotel.hotelmanagement.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hotel.hotelmanagement.config.CacheConfig;
import com.hotel.hotelmanagement.entity.Order;
import com.hotel.hotelmanagement.entity.Room;
import com.hotel.hotelmanagement.mapper.OrderMapper;
import com.hotel.hotelmanagement.mapper.RoomMapper;
import com.hotel.hotelmanagement.service.RoomAvailabilityService;
import com.hotel.hotelmanagement.service.StatisticsService;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * 统计 Service 实现类
 */
@Service
public class StatisticsServiceImpl implements StatisticsService {

    private final RoomMapper roomMapper;
    private final OrderMapper orderMapper;
    private final RoomAvailabilityService roomAvailabilityService;

    public StatisticsServiceImpl(RoomMapper roomMapper, OrderMapper orderMapper,
                                 RoomAvailabilityService roomAvailabilityService) {
        this.roomMapper = roomMapper;
        this.orderMapper = orderMapper;
        this.roomAvailabilityService = roomAvailabilityService;
    }

    /**
     * 看板聚合查询：房间全表 + 7 天订单区间聚合，是最重的读查询，因此整表缓存 60 秒。
     * 任何订单/房间写操作都会清空缓存，保证写完刷新即可看到新数据。
     */
    @Override
    @Cacheable(cacheNames = CacheConfig.DASHBOARD)
    public Map<String, Object> getDashboardData() {
        Map<String, Object> result = new LinkedHashMap<>();

        LocalDate today = LocalDate.now();

        // 1. 房态统计：可售性读 t_room.status，"空闲/已订/在住"由订单在「今天」这一天推导
        List<Room> rooms = roomMapper.selectList(null);
        roomAvailabilityService.fillAvailability(rooms, today, today.plusDays(1));

        int total = rooms.size();
        int idle = 0;
        int booked = 0;
        int checkedIn = 0;
        int repair = 0;
        for (Room room : rooms) {
            String availability = room.getAvailability();
            if (RoomAvailabilityService.REPAIR.equals(availability)) {
                repair++;
            } else if (RoomAvailabilityService.OCCUPIED.equals(availability)) {
                checkedIn++;
            } else if (RoomAvailabilityService.BOOKED.equals(availability)) {
                booked++;
            } else {
                idle++;
            }
        }

        // 出租率 = （已订 + 在住）/ 可售房数，停用维修的房间不计入分母
        int sellable = total - repair;
        long occupancyRate = sellable > 0 ? Math.round((booked + checkedIn) * 100.0 / sellable) : 0;

        Map<String, Object> roomStatus = new LinkedHashMap<>();
        roomStatus.put("total", total);
        roomStatus.put("sellable", sellable);
        roomStatus.put("idle", idle);
        roomStatus.put("booked", booked);
        roomStatus.put("checkedIn", checkedIn);
        roomStatus.put("repair", repair);
        roomStatus.put("occupancyRate", occupancyRate);
        result.put("roomStatus", roomStatus);

        // 2. 近7天收入趋势
        LocalDate startDate = today.minusDays(6);

        // 查询近7天内已退房(3)的订单，按【实际退房时间】归属收入
        List<Order> orders = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getStatus, 3)  // 已退房的订单才有最终收入
                .isNotNull(Order::getActualCheckOutTime)
                .ge(Order::getActualCheckOutTime, startDate.atStartOfDay())
                .lt(Order::getActualCheckOutTime, today.plusDays(1).atStartOfDay()));

        // 按日期汇总
        Map<String, BigDecimal> dailyRevenue = new LinkedHashMap<>();
        List<String> dateLabels = new ArrayList<>();
        List<BigDecimal> revenues = new ArrayList<>();

        for (int i = 0; i < 7; i++) {
            LocalDate date = startDate.plusDays(i);
            String dateLabel = date.toString();  // yyyy-MM-dd
            dateLabels.add(dateLabel);
            dailyRevenue.put(dateLabel, BigDecimal.ZERO);
        }

        for (Order order : orders) {
            // 收入按实际退房日归属，金额优先取最终结算金额
            String key = order.getActualCheckOutTime().toLocalDate().toString();
            BigDecimal amount = order.getFinalPrice() != null ? order.getFinalPrice() : order.getTotalPrice();
            if (amount != null) {
                dailyRevenue.merge(key, amount, BigDecimal::add);
            }
        }

        for (String date : dateLabels) {
            revenues.add(dailyRevenue.get(date));
        }

        Map<String, Object> revenueTrend = new LinkedHashMap<>();
        revenueTrend.put("dates", dateLabels);
        revenueTrend.put("revenues", revenues);
        result.put("revenueTrend", revenueTrend);

        return result;
    }
}
