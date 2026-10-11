package com.hotel.hotelmanagement.controller;

import com.hotel.hotelmanagement.annotation.RequirePermission;
import com.hotel.hotelmanagement.common.Result;
import com.hotel.hotelmanagement.service.StatisticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 统计看板 Controller
 */
@RestController
@RequestMapping("/statistics")
@RequirePermission("dashboard")
public class StatisticsController {

    private final StatisticsService statisticsService;

    public StatisticsController(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    /**
     * GET /statistics/dashboard - 获取统计数据
     * 返回：房态统计 + 出租率 + 近7天收入趋势
     */
    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard() {
        Map<String, Object> data = statisticsService.getDashboardData();
        return Result.success(data);
    }
}
