package com.hotel.hotelmanagement.service;

import com.hotel.hotelmanagement.dto.ClientBookDTO;
import com.hotel.hotelmanagement.dto.ClientLoginDTO;
import com.hotel.hotelmanagement.dto.ClientRegisterDTO;
import com.hotel.hotelmanagement.entity.Customer;
import com.hotel.hotelmanagement.entity.Order;
import com.hotel.hotelmanagement.entity.Room;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 客人端（自助预订） Service 接口
 */
public interface ClientService {

    /**
     * 客人注册（手机号唯一，写入客户表）
     */
    void register(ClientRegisterDTO dto);

    /**
     * 客人登录（手机号 + 密码），成功返回 token 和客户信息
     */
    Map<String, Object> login(ClientLoginDTO dto);

    /**
     * 根据 token 获取当前客人信息
     */
    Customer getByToken(String token);

    /**
     * 普通用户修改姓名（手机号是登录账号，不开放自助修改），返回更新后的个人信息
     */
    Customer updateProfile(Long customerId, String name);

    /**
     * 普通用户修改密码：原密码不正确时抛业务异常
     */
    void changePassword(Long customerId, String oldPassword, String newPassword);

    /**
     * 注销登录
     */
    void logout(String token);

    /**
     * 获取可预订房间列表（不含停用维修的房间），
     * 并按日期区间填充派生占用状态（available/booked/occupied）；
     * 区间为空时默认「今天 → 明天」。
     */
    List<Room> listBookableRooms(LocalDate checkInDate, LocalDate checkOutDate);

    /**
     * 客人下单预订（客户身份取自 token）
     */
    Order book(Long customerId, ClientBookDTO dto);

    /**
     * 查询当前客人的订单列表（填充房间号）
     */
    List<Order> myOrders(Long customerId);

    /**
     * 客人支付自己的待支付订单，返回支付后的订单
     */
    Order payOwn(Long customerId, Long orderId);

    /**
     * 客人取消自己的订单（待支付或已支付），已支付按退款规则退款，返回含退款金额的订单
     *
     * @param reason 取消原因，可为空
     */
    Order cancelOwn(Long customerId, Long orderId, String reason);
}
