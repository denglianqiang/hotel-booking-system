package com.hotel.hotelmanagement.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hotel.hotelmanagement.common.BusinessException;
import com.hotel.hotelmanagement.config.CacheConfig;
import com.hotel.hotelmanagement.dto.ClientBookDTO;
import com.hotel.hotelmanagement.dto.ClientLoginDTO;
import com.hotel.hotelmanagement.dto.ClientRegisterDTO;
import com.hotel.hotelmanagement.dto.OrderCreateDTO;
import com.hotel.hotelmanagement.entity.Customer;
import com.hotel.hotelmanagement.entity.Order;
import com.hotel.hotelmanagement.entity.Room;
import com.hotel.hotelmanagement.mapper.CustomerMapper;
import com.hotel.hotelmanagement.mapper.OrderMapper;
import com.hotel.hotelmanagement.mapper.RoomMapper;
import com.hotel.hotelmanagement.service.ClientService;
import com.hotel.hotelmanagement.service.OrderService;
import com.hotel.hotelmanagement.service.RoomAvailabilityService;
import com.hotel.hotelmanagement.util.PasswordUtil;
import com.hotel.hotelmanagement.util.TokenStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 客人端（自助预订） Service 实现类
 */
@Slf4j
@Service
public class ClientServiceImpl implements ClientService {

    private final CustomerMapper customerMapper;
    private final RoomMapper roomMapper;
    private final OrderMapper orderMapper;
    private final OrderService orderService;
    private final RoomAvailabilityService roomAvailabilityService;
    private final TokenStore tokenStore;

    public ClientServiceImpl(CustomerMapper customerMapper, RoomMapper roomMapper,
                             OrderMapper orderMapper, OrderService orderService,
                             RoomAvailabilityService roomAvailabilityService, TokenStore tokenStore) {
        this.customerMapper = customerMapper;
        this.roomMapper = roomMapper;
        this.orderMapper = orderMapper;
        this.orderService = orderService;
        this.roomAvailabilityService = roomAvailabilityService;
        this.tokenStore = tokenStore;
    }

    /**
     * 普通用户注册。
     *
     * <p>手机号已存在时分两种情况：
     * <ul>
     *   <li>已有密码：正常拒绝（重复注册）；</li>
     *   <li>无密码（前台代录的档案）：视为<b>认领</b>——把注册填写的姓名与密码写入该档案。
     *       否则代录档案会永远挡住该手机号自助注册，而管理端又无法发密码，形成死锁。</li>
     * </ul>
     */
    @Override
    public void register(ClientRegisterDTO dto) {
        Customer existing = customerMapper.selectOne(new LambdaQueryWrapper<Customer>()
                .eq(Customer::getPhone, dto.getPhone()));
        if (existing != null) {
            if (existing.getPassword() != null) {
                throw new BusinessException("该手机号已注册");
            }
            existing.setName(dto.getName());
            existing.setPassword(PasswordUtil.encode(dto.getPassword()));
            customerMapper.updateById(existing);
            log.info("普通用户认领前台代录档案成功: phone={}, customerId={}", existing.getPhone(), existing.getId());
            return;
        }
        Customer customer = new Customer();
        customer.setName(dto.getName());
        customer.setPhone(dto.getPhone());
        customer.setPassword(PasswordUtil.encode(dto.getPassword()));
        customer.setRegisterTime(LocalDateTime.now());
        customerMapper.insert(customer);
        log.info("普通用户注册成功: phone={}, customerId={}", customer.getPhone(), customer.getId());
    }

    /**
     * 客人登录
     */
    @Override
    public Map<String, Object> login(ClientLoginDTO dto) {
        Customer customer = customerMapper.selectOne(new LambdaQueryWrapper<Customer>()
                .eq(Customer::getPhone, dto.getPhone()));
        // 未注册与管理员代录（无密码）都引导注册
        if (customer == null || customer.getPassword() == null) {
            log.warn("普通用户登录失败（手机号未注册或无密码）: phone={}", dto.getPhone());
            throw new BusinessException("该手机号尚未注册，请先注册");
        }
        if (!PasswordUtil.matches(dto.getPassword(), customer.getPassword())) {
            log.warn("普通用户登录失败（密码错误）: phone={}", dto.getPhone());
            throw new BusinessException("手机号或密码错误");
        }
        // 历史 MD5 摘要登录成功后透明升级为 BCrypt
        if (PasswordUtil.isLegacy(customer.getPassword())) {
            customer.setPassword(PasswordUtil.encode(dto.getPassword()));
            customerMapper.updateById(customer);
        }

        String token = tokenStore.createClientToken(customer.getId());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("token", token);
        result.put("customerId", customer.getId());
        result.put("name", customer.getName());
        return result;
    }

    /**
     * 根据 token 获取当前客人信息
     */
    @Override
    public Customer getByToken(String token) {
        Long customerId = tokenStore.getClientUserId(token);
        if (customerId == null) {
            return null;
        }
        Customer customer = customerMapper.selectById(customerId);
        if (customer != null) {
            customer.setPassword(null);
        }
        return customer;
    }

    /**
     * 注销登录。与管理端共用"清理任意一侧 token"的实现，
     * 保证用户退出登录后服务端 token 立即失效。
     */
    @Override
    public void logout(String token) {
        tokenStore.removeAnyToken(token);
    }

    /**
     * 普通用户修改姓名。
     * 先查出完整实体再改名更新：updateById 走全字段写，行为不依赖字段更新策略。
     * 密码字段有 @JsonIgnore，直接返回实体不会把摘要下发前端；
     * 也因此<b>绝不能</b>在查出的实体上 setPassword(null)——同一事务内再次
     * selectById 会命中 MyBatis 一级缓存返回同一个对象，就地脱敏会把缓存实体改脏。
     */
    @Override
    public Customer updateProfile(Long customerId, String name) {
        Customer customer = requireCustomer(customerId);
        customer.setName(name);
        customerMapper.updateById(customer);
        log.info("普通用户修改资料: customerId={}", customerId);
        return requireCustomer(customerId);
    }

    /**
     * 普通用户修改密码：原密码不正确时拒绝。
     * 新密码一律存 BCrypt——历史 MD5 摘要的账号改密时自然完成升级。
     */
    @Override
    public void changePassword(Long customerId, String oldPassword, String newPassword) {
        Customer customer = requireCustomer(customerId);
        // 能登录的用户一定有密码；密码为空视同校验失败，防御前台代录档案被异常赋予 token 的情况
        if (customer.getPassword() == null || !PasswordUtil.matches(oldPassword, customer.getPassword())) {
            throw new BusinessException("原密码不正确");
        }
        customer.setPassword(PasswordUtil.encode(newPassword));
        customerMapper.updateById(customer);
        log.info("普通用户修改密码成功: customerId={}", customerId);
    }

    /** 查询用户并兜底存在性校验 */
    private Customer requireCustomer(Long customerId) {
        Customer customer = customerMapper.selectById(customerId);
        if (customer == null) {
            throw new BusinessException("用户不存在");
        }
        return customer;
    }

    /**
     * 获取可预订房间列表（不含停用维修的房间），并按日期区间填充派生占用状态。
     * 与管理端房态图分别缓存（结果口径不同：这里剔除维修房），同样只服务展示读路径。
     */
    @Override
    @Cacheable(cacheNames = CacheConfig.BOOKABLE_ROOMS,
            key = "#checkInDate + '|' + #checkOutDate")
    public List<Room> listBookableRooms(LocalDate checkInDate, LocalDate checkOutDate) {
        List<Room> rooms = roomMapper.selectList(new LambdaQueryWrapper<Room>()
                .ne(Room::getStatus, 3)
                .orderByAsc(Room::getRoomNumber));
        return roomAvailabilityService.fillAvailability(rooms, checkInDate, checkOutDate);
    }

    /**
     * 客人下单预订（防重复预订、自动计价复用管理端逻辑）
     */
    @Override
    public Order book(Long customerId, ClientBookDTO dto) {
        OrderCreateDTO createDTO = new OrderCreateDTO();
        createDTO.setRoomId(dto.getRoomId());
        createDTO.setCustomerId(customerId);
        createDTO.setCheckInDate(dto.getCheckInDate());
        createDTO.setCheckOutDate(dto.getCheckOutDate());
        return orderService.createOrder(createDTO);
    }

    /**
     * 查询当前客人的订单列表
     */
    @Override
    public List<Order> myOrders(Long customerId) {
        List<Order> orders = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getCustomerId, customerId)
                .orderByDesc(Order::getCreateTime));
        if (!orders.isEmpty()) {
            Set<Long> roomIds = orders.stream().map(Order::getRoomId).collect(Collectors.toSet());
            Map<Long, String> roomMap = roomMapper.selectBatchIds(roomIds).stream()
                    .collect(Collectors.toMap(Room::getId, Room::getRoomNumber));
            orders.forEach(o -> o.setRoomNumber(roomMap.get(o.getRoomId())));
        }
        return orders;
    }

    /**
     * 客人支付自己的待支付订单。
     * 先校验订单归属，再复用管理端的统一支付逻辑（含支付前房态复核），避免两套支付实现口径不一致。
     */
    @Override
    public Order payOwn(Long customerId, Long orderId) {
        getOwnOrder(customerId, orderId);
        return orderService.payOrder(orderId, currentCustomerName(customerId));
    }

    /**
     * 客人取消自己的订单（待支付或已支付）。
     * 已支付订单的退款金额由统一退款规则计算，客人端不再自行实现。
     */
    @Override
    public Order cancelOwn(Long customerId, Long orderId, String reason) {
        getOwnOrder(customerId, orderId);
        return orderService.cancelOrder(orderId, currentCustomerName(customerId), reason);
    }

    /** 取当前客人姓名作为操作人 */
    private String currentCustomerName(Long customerId) {
        Customer customer = customerMapper.selectById(customerId);
        return customer != null ? customer.getName() : "普通用户";
    }

    /**
     * 查询本人的订单，非本人或不存在时抛异常
     */
    private Order getOwnOrder(Long customerId, Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        if (!order.getCustomerId().equals(customerId)) {
            throw new BusinessException("无权操作该订单");
        }
        return order;
    }
}
