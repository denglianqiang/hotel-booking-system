package com.hotel.hotelmanagement.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hotel.hotelmanagement.annotation.RequirePermission;
import com.hotel.hotelmanagement.common.Result;
import com.hotel.hotelmanagement.dto.CheckInQueryDTO;
import com.hotel.hotelmanagement.entity.CheckIn;
import com.hotel.hotelmanagement.entity.Order;
import com.hotel.hotelmanagement.service.CheckInService;
import com.hotel.hotelmanagement.service.OrderService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 入住登记 Controller
 *
 * <p>t_check_in 原本"只写不读"（退房时插入记录，却没有查询入口），
 * 这里把它变成可用功能：在住客人列表 + 历史入住记录查询。
 */
@RestController
@RequestMapping("/checkin")
@RequirePermission("checkins")
public class CheckInController {

    private final CheckInService checkInService;
    private final OrderService orderService;

    public CheckInController(CheckInService checkInService, OrderService orderService) {
        this.checkInService = checkInService;
        this.orderService = orderService;
    }

    /**
     * GET /checkin/inhouse - 在住客人列表（订单状态=已入住），含房间号、实际入住时间、预计退房日
     */
    @GetMapping("/inhouse")
    public Result<List<Order>> inHouse() {
        return Result.success(orderService.listInHouse());
    }

    /**
     * GET /checkin/list - 入住登记记录分页查询
     * 支持按房间号、客户姓名（均模糊）与入住日期区间（含止日）过滤
     */
    @GetMapping("/list")
    public Result<IPage<CheckIn>> list(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String roomNumber,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        CheckInQueryDTO query = new CheckInQueryDTO();
        query.setCurrent(current);
        query.setSize(size);
        query.setRoomNumber(roomNumber);
        query.setCustomerName(customerName);
        query.setStartDate(startDate);
        query.setEndDate(endDate);
        return Result.success(checkInService.queryPage(query));
    }
}
