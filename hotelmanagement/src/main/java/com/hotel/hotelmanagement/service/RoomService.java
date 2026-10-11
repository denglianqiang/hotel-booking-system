package com.hotel.hotelmanagement.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.hotel.hotelmanagement.dto.RoomDTO;
import com.hotel.hotelmanagement.dto.RoomQueryDTO;
import com.hotel.hotelmanagement.entity.Room;

import java.time.LocalDate;
import java.util.List;

/**
 * 房间表 Service 接口
 */
public interface RoomService extends IService<Room> {

    /**
     * 分页查询房间列表（支持按类型/状态/楼层筛选）
     */
    IPage<Room> queryPage(RoomQueryDTO query);

    /**
     * 获取所有房间列表（房态图）。
     * 房间的可订/已订/在住由订单在指定日期区间内推导，因此需要传入区间；
     * 区间为空时默认「今天 → 明天」。
     */
    List<Room> listAll(LocalDate checkInDate, LocalDate checkOutDate);

    /**
     * 新增房间（默认正常销售）
     */
    void saveRoom(RoomDTO dto);

    /**
     * 编辑房间
     */
    void updateRoom(RoomDTO dto);

    /**
     * 删除房间
     */
    void deleteRoom(Long id);

    /**
     * 设置房间可售性：只允许 0正常销售 / 3停用维修
     * （已订/在住由订单推导，不允许手工设置）
     */
    void setStatus(Long id, Integer status);
}
