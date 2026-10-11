package com.hotel.hotelmanagement.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.hotel.hotelmanagement.dto.CheckInQueryDTO;
import com.hotel.hotelmanagement.entity.CheckIn;

/**
 * 入住记录表 Service 接口
 *
 * <p>改造前这张表"只写不读"：办理退房时插入记录，却没有任何查询接口和页面。
 */
public interface CheckInService extends IService<CheckIn> {

    /**
     * 分页查询入住登记记录（按房间号/客户姓名/入住日期区间过滤），
     * 并填充房间号与客户姓名等展示字段。
     */
    IPage<CheckIn> queryPage(CheckInQueryDTO query);
}
