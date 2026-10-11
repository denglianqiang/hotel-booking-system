package com.hotel.hotelmanagement.service;

import java.util.Map;

/**
 * 统计 Service 接口
 */
public interface StatisticsService {

    /**
     * 获取看板数据
     * 包含：房态统计、入住率、近7天收入趋势
     */
    Map<String, Object> getDashboardData();
}
