package com.hotel.hotelmanagement.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hotel.hotelmanagement.dto.CheckInQueryDTO;
import com.hotel.hotelmanagement.entity.CheckIn;
import com.hotel.hotelmanagement.entity.Customer;
import com.hotel.hotelmanagement.entity.Room;
import com.hotel.hotelmanagement.mapper.CheckInMapper;
import com.hotel.hotelmanagement.mapper.CustomerMapper;
import com.hotel.hotelmanagement.mapper.RoomMapper;
import com.hotel.hotelmanagement.service.CheckInService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 入住记录表 Service 实现类
 */
@Service
public class CheckInServiceImpl extends ServiceImpl<CheckInMapper, CheckIn> implements CheckInService {

    private final RoomMapper roomMapper;
    private final CustomerMapper customerMapper;

    public CheckInServiceImpl(RoomMapper roomMapper, CustomerMapper customerMapper) {
        this.roomMapper = roomMapper;
        this.customerMapper = customerMapper;
    }

    @Override
    public IPage<CheckIn> queryPage(CheckInQueryDTO query) {
        Page<CheckIn> page = new Page<>(query.resolveCurrent(), query.resolveSize());

        // 房间号、客户姓名不是 t_check_in 的字段，先按条件把主键解析出来再过滤
        Set<Long> roomIds = null;
        if (StringUtils.hasText(query.getRoomNumber())) {
            roomIds = roomMapper.selectList(new LambdaQueryWrapper<Room>()
                            .like(Room::getRoomNumber, query.getRoomNumber())).stream()
                    .map(Room::getId).collect(Collectors.toSet());
            if (roomIds.isEmpty()) {
                return page; // 没有匹配的房间，直接返回空页
            }
        }
        Set<Long> customerIds = null;
        if (StringUtils.hasText(query.getCustomerName())) {
            customerIds = customerMapper.selectList(new LambdaQueryWrapper<Customer>()
                            .like(Customer::getName, query.getCustomerName())).stream()
                    .map(Customer::getId).collect(Collectors.toSet());
            if (customerIds.isEmpty()) {
                return page;
            }
        }

        LambdaQueryWrapper<CheckIn> wrapper = new LambdaQueryWrapper<CheckIn>()
                .in(roomIds != null, CheckIn::getRoomId, roomIds)
                .in(customerIds != null, CheckIn::getCustomerId, customerIds)
                // 日期区间按"入住时刻"过滤，止日含当天
                .ge(query.getStartDate() != null, CheckIn::getCheckInTime,
                        query.getStartDate() != null ? query.getStartDate().atStartOfDay() : null)
                .lt(query.getEndDate() != null, CheckIn::getCheckInTime,
                        query.getEndDate() != null ? query.getEndDate().plusDays(1).atStartOfDay() : null)
                .orderByDesc(CheckIn::getCheckInTime)
                .orderByDesc(CheckIn::getId);

        IPage<CheckIn> result = this.page(page, wrapper);
        fillDisplayFields(result.getRecords());
        return result;
    }

    /**
     * 批量填充房间号与客户姓名，避免逐行查库
     */
    private void fillDisplayFields(List<CheckIn> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        Set<Long> roomIds = records.stream().map(CheckIn::getRoomId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> customerIds = records.stream().map(CheckIn::getCustomerId)
                .filter(Objects::nonNull).collect(Collectors.toSet());

        Map<Long, String> roomMap = roomIds.isEmpty() ? Map.of()
                : roomMapper.selectBatchIds(roomIds).stream()
                        .collect(Collectors.toMap(Room::getId, Room::getRoomNumber));
        Map<Long, String> customerMap = customerIds.isEmpty() ? Map.of()
                : customerMapper.selectBatchIds(customerIds).stream()
                        .collect(Collectors.toMap(Customer::getId, Customer::getName));

        for (CheckIn checkIn : records) {
            checkIn.setRoomNumber(roomMap.get(checkIn.getRoomId()));
            checkIn.setCustomerName(customerMap.get(checkIn.getCustomerId()));
        }
    }
}
