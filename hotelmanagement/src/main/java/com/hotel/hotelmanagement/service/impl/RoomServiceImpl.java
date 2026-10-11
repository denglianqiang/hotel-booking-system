package com.hotel.hotelmanagement.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hotel.hotelmanagement.common.BusinessException;
import com.hotel.hotelmanagement.config.CacheConfig;
import com.hotel.hotelmanagement.config.EvictDisplayCaches;
import com.hotel.hotelmanagement.dto.RoomDTO;
import com.hotel.hotelmanagement.dto.RoomQueryDTO;
import com.hotel.hotelmanagement.entity.Order;
import com.hotel.hotelmanagement.entity.Room;
import com.hotel.hotelmanagement.mapper.OrderMapper;
import com.hotel.hotelmanagement.mapper.RoomMapper;
import com.hotel.hotelmanagement.service.RoomAvailabilityService;
import com.hotel.hotelmanagement.service.RoomService;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;

/**
 * 房间表 Service 实现类
 */
@Service
public class RoomServiceImpl extends ServiceImpl<RoomMapper, Room> implements RoomService {

    private final RoomAvailabilityService roomAvailabilityService;
    private final OrderMapper orderMapper;

    public RoomServiceImpl(RoomAvailabilityService roomAvailabilityService, OrderMapper orderMapper) {
        this.roomAvailabilityService = roomAvailabilityService;
        this.orderMapper = orderMapper;
    }

    /**
     * 分页查询房间列表
     */
    @Override
    public IPage<Room> queryPage(RoomQueryDTO query) {
        Page<Room> page = new Page<>(query.getCurrent(), query.getSize());
        LambdaQueryWrapper<Room> wrapper = new LambdaQueryWrapper<>();
        // 按房间类型筛选
        wrapper.eq(StringUtils.hasText(query.getType()), Room::getType, query.getType());
        // 按可售性筛选（0正常销售 / 3停用维修）
        wrapper.eq(query.getStatus() != null, Room::getStatus, query.getStatus());
        // 按楼层筛选
        wrapper.eq(query.getFloor() != null, Room::getFloor, query.getFloor());
        // 按房间号升序排列
        wrapper.orderByAsc(Room::getRoomNumber);
        return this.page(page, wrapper);
    }

    /**
     * 获取所有房间列表（房态图），并按日期区间填充派生占用状态。
     *
     * <p>缓存只服务展示：结果按日期区间缓存，任何订单/房间写操作都会整体清空。
     * 注意缓存值「可订」不代表一定能下单——下单走 createOrder 的行锁 + 实时占用校验。
     */
    @Override
    @Cacheable(cacheNames = CacheConfig.ROOM_STATUS,
            key = "#checkInDate + '|' + #checkOutDate")
    public List<Room> listAll(LocalDate checkInDate, LocalDate checkOutDate) {
        List<Room> rooms = this.list(new LambdaQueryWrapper<Room>().orderByAsc(Room::getRoomNumber));
        return roomAvailabilityService.fillAvailability(rooms, checkInDate, checkOutDate);
    }

    /**
     * 新增房间
     */
    @Override
    @EvictDisplayCaches
    public void saveRoom(RoomDTO dto) {
        // 校验房间号唯一
        long count = this.count(new LambdaQueryWrapper<Room>()
                .eq(Room::getRoomNumber, dto.getRoomNumber()));
        if (count > 0) {
            throw new BusinessException("房间号已存在");
        }
        Room room = new Room();
        room.setRoomNumber(dto.getRoomNumber());
        room.setType(dto.getType());
        room.setPrice(dto.getPrice());
        room.setFloor(dto.getFloor());
        room.setDescription(dto.getDescription());
        room.setStatus(0); // 新增房间默认正常销售
        this.save(room);
    }

    /**
     * 编辑房间
     */
    @Override
    @EvictDisplayCaches
    public void updateRoom(RoomDTO dto) {
        Room room = this.getById(dto.getId());
        if (room == null) {
            throw new BusinessException("房间不存在");
        }
        // 校验房间号唯一（排除自身）
        long count = this.count(new LambdaQueryWrapper<Room>()
                .eq(Room::getRoomNumber, dto.getRoomNumber())
                .ne(Room::getId, dto.getId()));
        if (count > 0) {
            throw new BusinessException("房间号已被其他房间使用");
        }
        room.setRoomNumber(dto.getRoomNumber());
        room.setType(dto.getType());
        room.setPrice(dto.getPrice());
        room.setFloor(dto.getFloor());
        room.setDescription(dto.getDescription());
        // 有未完结订单（待支付/已支付/已入住）的房间不允许改可售性
        if (dto.getStatus() != null && !dto.getStatus().equals(room.getStatus())) {
            if (roomAvailabilityService.hasUnfinishedOrders(room.getId())) {
                throw new BusinessException("该房间有未完成的订单，暂不能修改状态");
            }
            checkManualStatus(dto.getStatus());
            room.setStatus(dto.getStatus());
        }
        this.updateById(room);
    }

    /**
     * 删除房间。
     *
     * <p>只要有订单（含已退房的历史订单）就不允许删除：t_order.room_id 是逻辑外键，
     * 删掉房间会让历史订单的房号变成空值。房间下架请用「设为维修（停用）」。
     */
    @Override
    @EvictDisplayCaches
    public void deleteRoom(Long id) {
        Room room = this.getById(id);
        if (room == null) {
            throw new BusinessException("房间不存在");
        }
        long orderCount = orderMapper.selectCount(new LambdaQueryWrapper<Order>()
                .eq(Order::getRoomId, id));
        if (orderCount > 0) {
            throw new BusinessException("该房间存在 " + orderCount + " 笔订单，删除会让订单失去关联，请改用「设为维修」下架房间");
        }
        this.removeById(id);
    }

    /**
     * 设置房间可售性（正常销售 <-> 停用维修）
     */
    @Override
    @EvictDisplayCaches
    public void setStatus(Long id, Integer status) {
        Room room = this.getById(id);
        if (room == null) {
            throw new BusinessException("房间不存在");
        }
        checkManualStatus(status);
        if (roomAvailabilityService.hasUnfinishedOrders(id)) {
            throw new BusinessException("该房间有未完成的订单，暂不能修改状态");
        }
        room.setStatus(status);
        this.updateById(room);
    }

    /**
     * 手工设置只允许 正常销售(0)/停用维修(3)，已订/在住由订单推导，不允许手工设置
     */
    private void checkManualStatus(Integer status) {
        if (status == null || (status != 0 && status != 3)) {
            throw new BusinessException("只能把房间设为正常销售或停用维修");
        }
    }
}
