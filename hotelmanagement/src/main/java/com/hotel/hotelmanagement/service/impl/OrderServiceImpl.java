package com.hotel.hotelmanagement.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hotel.hotelmanagement.common.BusinessException;
import com.hotel.hotelmanagement.config.EvictDisplayCaches;
import com.hotel.hotelmanagement.config.RefundProperties;
import com.hotel.hotelmanagement.dto.OrderCreateDTO;
import com.hotel.hotelmanagement.dto.OrderOperateDTO;
import com.hotel.hotelmanagement.dto.OrderQueryDTO;
import com.hotel.hotelmanagement.entity.CheckIn;
import com.hotel.hotelmanagement.entity.Order;
import com.hotel.hotelmanagement.entity.OrderLog;
import com.hotel.hotelmanagement.entity.Room;
import com.hotel.hotelmanagement.mapper.OrderLogMapper;
import com.hotel.hotelmanagement.mapper.OrderMapper;
import com.hotel.hotelmanagement.mapper.RoomMapper;
import com.hotel.hotelmanagement.mapper.SeqMapper;
import com.hotel.hotelmanagement.service.CheckInService;
import com.hotel.hotelmanagement.service.CustomerService;
import com.hotel.hotelmanagement.service.OrderService;
import com.hotel.hotelmanagement.service.RoomAvailabilityService;
import com.hotel.hotelmanagement.service.RoomService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 订单表 Service 实现类
 *
 * <p>状态机：
 * <pre>
 * 0 待支付 ──支付──> 1 已支付 ──入住──> 2 已入住 ──退房──> 3 已退房
 *    ├──取消──> 4 已取消              └──取消(退款)──> 4 已取消
 *    └──超时未支付自动取消──> 4 已取消
 * </pre>
 * 每一次流转都会写入 t_order_log，保证可追溯。
 */
@Slf4j
@Service
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements OrderService {

    /** 系统自动动作的操作人名称 */
    private static final String SYSTEM_OPERATOR = "系统";

    private final RoomService roomService;
    private final CustomerService customerService;
    private final CheckInService checkInService;
    private final RoomMapper roomMapper;
    private final RoomAvailabilityService roomAvailabilityService;
    private final OrderLogMapper orderLogMapper;
    private final RefundProperties refundProperties;
    private final SeqMapper seqMapper;

    /** 待支付订单的占位时长（分钟），与房态判定、自动取消保持一致 */
    private final long pendingPaymentMinutes;

    public OrderServiceImpl(RoomService roomService, CustomerService customerService, CheckInService checkInService,
                            RoomMapper roomMapper, RoomAvailabilityService roomAvailabilityService,
                            OrderLogMapper orderLogMapper, RefundProperties refundProperties, SeqMapper seqMapper,
                            @Value("${hotel.order.pending-payment-minutes:30}") long pendingPaymentMinutes) {
        this.roomService = roomService;
        this.customerService = customerService;
        this.checkInService = checkInService;
        this.roomMapper = roomMapper;
        this.roomAvailabilityService = roomAvailabilityService;
        this.orderLogMapper = orderLogMapper;
        this.refundProperties = refundProperties;
        this.seqMapper = seqMapper;
        this.pendingPaymentMinutes = pendingPaymentMinutes;
    }

    /**
     * 创建预订订单
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @EvictDisplayCaches
    public Order createOrder(OrderCreateDTO dto) {
        // 1. 校验日期：退房日期必须晚于入住日期，且入住日期不能早于今天
        if (!dto.getCheckOutDate().isAfter(dto.getCheckInDate())) {
            throw new BusinessException("退房日期必须晚于入住日期");
        }
        if (dto.getCheckInDate().isBefore(LocalDate.now())) {
            throw new BusinessException("入住日期不能早于今天");
        }

        // 2. 对房间行加锁：同房间的并发下单被串行化，避免两人同时通过冲突校验
        //    （日期区间重叠无法用数据库唯一键约束，只能靠行锁）
        Room room = roomMapper.selectByIdForUpdate(dto.getRoomId());
        if (room == null) {
            throw new BusinessException("房间不存在");
        }
        // 停用维修的房间不能预订
        if (room.getStatus() != null && room.getStatus() == 3) {
            throw new BusinessException("房间维修中，暂不可预订");
        }

        // 3. 校验客户是否存在
        var customer = customerService.getById(dto.getCustomerId());
        if (customer == null) {
            throw new BusinessException("客户不存在");
        }

        // 4. 占用校验：统一走 RoomAvailabilityService（含未超时的待支付订单）
        if (!roomAvailabilityService.isRoomAvailable(dto.getRoomId(), dto.getCheckInDate(), dto.getCheckOutDate(), null)) {
            throw new BusinessException("该房间在选定日期范围内已被预订，请更换日期或房间");
        }

        // 5. 计算总价：天数 × 房间单价
        LocalDateTime now = LocalDateTime.now();
        long days = ChronoUnit.DAYS.between(dto.getCheckInDate(), dto.getCheckOutDate());
        BigDecimal totalPrice = room.getPrice().multiply(BigDecimal.valueOf(days));

        // 6. 构建订单
        Order order = new Order();
        order.setOrderNo(nextOrderNo(LocalDate.now()));
        order.setRoomId(dto.getRoomId());
        order.setCustomerId(dto.getCustomerId());
        order.setCustomerName(customer.getName());
        order.setCheckInDate(dto.getCheckInDate());
        order.setCheckOutDate(dto.getCheckOutDate());
        order.setTotalPrice(totalPrice);
        order.setStatus(0); // 待支付
        order.setCreateTime(now);
        order.setPayDeadline(now.plusMinutes(pendingPaymentMinutes));
        order.setRefundAmount(BigDecimal.ZERO);

        this.save(order);
        logTransition(order, null, 0, resolveOperator(customer.getName()),
                "创建预订，待支付（" + dto.getCheckInDate() + " 至 " + dto.getCheckOutDate() + "）");
        log.info("创建订单 {}：房间={}，客户={}，{} 至 {}，金额={}",
                order.getOrderNo(), room.getRoomNumber(), customer.getName(),
                dto.getCheckInDate(), dto.getCheckOutDate(), totalPrice);
        return order;
    }

    /**
     * 办理入住：写入实际入住时间，并生成入住登记记录
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @EvictDisplayCaches
    public void checkIn(OrderOperateDTO dto, String operator) {
        // 1. 查询订单
        Order order = this.getById(dto.getOrderId());
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        // 只有已支付(1)的订单才能办理入住
        if (order.getStatus() != 1) {
            throw new BusinessException("只有已支付的订单才能办理入住，当前订单状态: " + order.getStatus());
        }

        // 2. 入住前确认该房间没有被别的订单占用（例如上一位客人还没退房）
        if (!roomAvailabilityService.isRoomAvailable(order.getRoomId(), order.getCheckInDate(),
                order.getCheckOutDate(), order.getId())) {
            throw new BusinessException("该房间在入住日期内已被其他订单占用，无法办理入住");
        }

        // 3. 更新订单：状态改为已入住，记录实际入住时间
        LocalDateTime now = LocalDateTime.now();
        order.setStatus(2);
        order.setActualCheckInTime(now);
        this.updateById(order);
        logTransition(order, 1, 2, resolveOperator(operator), "办理入住");

        // 4. 生成入住登记记录（真实入住时刻 + 真实操作人）
        CheckIn checkIn = new CheckIn();
        checkIn.setOrderId(order.getId());
        checkIn.setRoomId(order.getRoomId());
        checkIn.setCustomerId(order.getCustomerId());
        checkIn.setCheckInTime(now);
        checkIn.setActualPrice(order.getTotalPrice());
        checkIn.setOperator(resolveOperator(operator));
        checkInService.save(checkIn);
        log.info("订单 {} 办理入住：房间={}，客户={}，操作人={}",
                order.getOrderNo(), order.getRoomId(), order.getCustomerName(), resolveOperator(operator));
    }

    /**
     * 办理退房：写入实际退房时间与结算金额，回填入住登记记录。
     * 注意：计划退房日 checkOutDate 保留不变，实际退房时间单独记录。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @EvictDisplayCaches
    public void checkOut(OrderOperateDTO dto, String operator) {
        // 1. 查询订单
        Order order = this.getById(dto.getOrderId());
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        // 只有已入住(2)的订单才能办理退房
        if (order.getStatus() != 2) {
            throw new BusinessException("只有已入住的订单才能办理退房，当前订单状态: " + order.getStatus());
        }

        // 2. 查询房间信息（结算单价以房间当前单价为准）
        Room room = roomService.getById(order.getRoomId());
        if (room == null) {
            throw new BusinessException("关联房间不存在");
        }

        // 3. 结算：按计划夜数 × 房间单价
        long days = ChronoUnit.DAYS.between(order.getCheckInDate(), order.getCheckOutDate());
        BigDecimal finalPrice = room.getPrice().multiply(BigDecimal.valueOf(days));

        LocalDateTime now = LocalDateTime.now();
        order.setFinalPrice(finalPrice);
        order.setActualCheckOutTime(now);
        order.setStatus(3);
        this.updateById(order);
        logTransition(order, 2, 3, resolveOperator(operator), "办理退房，结算金额 ¥" + finalPrice);

        // 4. 回填入住登记记录（历史数据可能没有登记记录，此时补一条）
        CheckIn checkIn = checkInService.getOne(new LambdaQueryWrapper<CheckIn>()
                .eq(CheckIn::getOrderId, order.getId())
                .orderByDesc(CheckIn::getId)
                .last("LIMIT 1"));
        if (checkIn == null) {
            checkIn = new CheckIn();
            checkIn.setOrderId(order.getId());
            checkIn.setRoomId(order.getRoomId());
            checkIn.setCustomerId(order.getCustomerId());
            checkIn.setCheckInTime(order.getActualCheckInTime() != null
                    ? order.getActualCheckInTime() : order.getCheckInDate().atStartOfDay());
            checkIn.setCheckOutTime(now);
            checkIn.setActualPrice(finalPrice);
            checkIn.setOperator(resolveOperator(operator));
            checkInService.save(checkIn);
        } else {
            checkIn.setCheckOutTime(now);
            checkIn.setActualPrice(finalPrice);
            checkInService.updateById(checkIn);
        }
        log.info("订单 {} 办理退房：结算金额={}，操作人={}",
                order.getOrderNo(), finalPrice, resolveOperator(operator));
    }

    /**
     * 分页查询订单列表（联查房间号）
     */
    @Override
    public IPage<Order> queryPage(OrderQueryDTO query) {
        Page<Order> page = new Page<>(query.getCurrent(), query.getSize());
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        // 按订单号模糊查询
        wrapper.like(StringUtils.hasText(query.getOrderNo()), Order::getOrderNo, query.getOrderNo());
        // 按客户姓名模糊查询
        wrapper.like(StringUtils.hasText(query.getCustomerName()), Order::getCustomerName, query.getCustomerName());
        // 按状态精确查询
        wrapper.eq(query.getStatus() != null, Order::getStatus, query.getStatus());
        // 按创建时间倒序
        wrapper.orderByDesc(Order::getCreateTime);
        IPage<Order> result = this.page(page, wrapper);

        // 批量填充 roomNumber
        List<Order> records = result.getRecords();
        if (records != null && !records.isEmpty()) {
            Set<Long> roomIds = records.stream().map(Order::getRoomId).collect(Collectors.toSet());
            Map<Long, String> roomMap = roomService.listByIds(roomIds).stream()
                    .collect(Collectors.toMap(Room::getId, Room::getRoomNumber));
            records.forEach(o -> o.setRoomNumber(roomMap.get(o.getRoomId())));
        }
        return result;
    }

    /**
     * 支付订单（0待支付 → 1已支付）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @EvictDisplayCaches
    public Order payOrder(Long orderId, String operator) {
        Order order = this.getById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        if (order.getStatus() != 0) {
            throw new BusinessException("只有待支付的订单才能支付，当前订单状态: " + order.getStatus());
        }

        // 支付前重新校验房态：下单时可能不冲突，但此后该房间已被别的订单抢先支付/入住。
        // 同样先锁房间行，把并发支付串行化，避免两人同时支付成功造成超卖。
        roomMapper.selectByIdForUpdate(order.getRoomId());
        if (!roomAvailabilityService.isRoomAvailable(order.getRoomId(), order.getCheckInDate(),
                order.getCheckOutDate(), order.getId())) {
            throw new BusinessException("该房间在您的入住日期内已被其他订单占用，无法支付，请取消本订单");
        }

        order.setStatus(1);
        order.setPaidTime(LocalDateTime.now());
        this.updateById(order);
        logTransition(order, 0, 1, resolveOperator(operator), "支付订单，金额 ¥" + order.getTotalPrice());
        log.info("订单 {} 支付成功：金额={}，操作人={}", order.getOrderNo(), order.getTotalPrice(), resolveOperator(operator));
        return order;
    }

    /**
     * 取消订单：待支付(0) 与 已支付(1) 都可取消；已支付按退款规则计算退款金额。
     * 已入住(2) 只能走退房，不允许直接取消。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @EvictDisplayCaches
    public Order cancelOrder(Long orderId, String operator, String reason) {
        Order order = this.getById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        if (order.getStatus() != 0 && order.getStatus() != 1) {
            throw new BusinessException("只有待支付或已支付的订单才能取消，当前订单状态: " + order.getStatus());
        }

        int fromStatus = order.getStatus();
        BigDecimal refund = fromStatus == 1 ? calcRefund(order) : BigDecimal.ZERO;

        order.setStatus(4);
        order.setCancelTime(LocalDateTime.now());
        order.setCancelReason(StringUtils.hasText(reason) ? reason : "用户取消");
        order.setRefundAmount(refund);
        this.updateById(order);

        String remark = fromStatus == 1
                ? "取消已支付订单，退款 ¥" + refund + "（" + order.getCancelReason() + "）"
                : "取消待支付订单（" + order.getCancelReason() + "）";
        logTransition(order, fromStatus, 4, resolveOperator(operator), remark);
        log.info("订单 {} 已取消：原状态={}，退款={}，原因={}，操作人={}",
                order.getOrderNo(), fromStatus, refund, order.getCancelReason(), resolveOperator(operator));
        return order;
    }

    /**
     * 查询订单的操作轨迹（倒序）
     */
    @Override
    public List<OrderLog> listLogs(Long orderId) {
        return orderLogMapper.selectList(new LambdaQueryWrapper<OrderLog>()
                .eq(OrderLog::getOrderId, orderId)
                .orderByDesc(OrderLog::getCreateTime)
                .orderByDesc(OrderLog::getId));
    }

    /**
     * 定时把超时未支付的订单自动取消，释放房间占用。
     * 启动后 1 分钟首次执行，之后每 5 分钟一次。
     */
    @Override
    @Scheduled(initialDelay = 60_000L, fixedDelay = 300_000L)
    @Transactional(rollbackFor = Exception.class)
    @EvictDisplayCaches
    public int autoCancelExpiredOrders() {
        LocalDateTime now = LocalDateTime.now();
        List<Order> expired = this.list(new LambdaQueryWrapper<Order>()
                .eq(Order::getStatus, 0)
                // 有截止时间的按截止时间判断；历史数据没有截止时间则按创建时间兜底
                .and(w -> w.lt(Order::getPayDeadline, now)
                        .or(x -> x.isNull(Order::getPayDeadline)
                                .lt(Order::getCreateTime, now.minusMinutes(pendingPaymentMinutes)))));

        for (Order order : expired) {
            order.setStatus(4);
            order.setCancelTime(now);
            order.setCancelReason("超时未支付，系统自动取消");
            order.setRefundAmount(BigDecimal.ZERO);
            this.updateById(order);
            logTransition(order, 0, 4, SYSTEM_OPERATOR, "超时未支付，系统自动取消");
        }
        if (!expired.isEmpty()) {
            log.info("超时未支付自动取消 {} 笔订单，房间占用已释放", expired.size());
        }
        return expired.size();
    }

    /**
     * 在住客人列表（订单状态=已入住）
     */
    @Override
    public List<Order> listInHouse() {
        List<Order> orders = this.list(new LambdaQueryWrapper<Order>()
                .eq(Order::getStatus, 2)
                .orderByAsc(Order::getCheckOutDate));
        if (orders.isEmpty()) {
            return orders;
        }
        Set<Long> roomIds = orders.stream().map(Order::getRoomId).collect(Collectors.toSet());
        Map<Long, String> roomMap = roomService.listByIds(roomIds).stream()
                .collect(Collectors.toMap(Room::getId, Room::getRoomNumber));
        orders.forEach(o -> o.setRoomNumber(roomMap.get(o.getRoomId())));
        return orders;
    }

    /**
     * 退款规则：
     * 距入住日 >= freeCancelDays 天 → 全额退；
     * 否则扣除首晚房费后退余额（chargeFirstNight=false 时仍全额退）。
     */
    private BigDecimal calcRefund(Order order) {
        BigDecimal total = order.getTotalPrice() != null ? order.getTotalPrice() : BigDecimal.ZERO;
        long daysBefore = ChronoUnit.DAYS.between(LocalDate.now(), order.getCheckInDate());
        if (daysBefore >= refundProperties.getFreeCancelDays() || !refundProperties.isChargeFirstNight()) {
            return total;
        }
        long nights = ChronoUnit.DAYS.between(order.getCheckInDate(), order.getCheckOutDate());
        if (nights <= 0) {
            return total;
        }
        BigDecimal firstNight = total.divide(BigDecimal.valueOf(nights), 2, RoundingMode.HALF_UP);
        BigDecimal refund = total.subtract(firstNight);
        return refund.compareTo(BigDecimal.ZERO) > 0 ? refund : BigDecimal.ZERO;
    }

    /**
     * 写一条订单操作日志
     */
    private void logTransition(Order order, Integer fromStatus, Integer toStatus, String operator, String remark) {
        OrderLog log = new OrderLog();
        log.setOrderId(order.getId());
        log.setFromStatus(fromStatus);
        log.setToStatus(toStatus);
        log.setOperator(resolveOperator(operator));
        log.setRemark(remark);
        log.setCreateTime(LocalDateTime.now());
        orderLogMapper.insert(log);
    }

    /** 操作人兜底，避免日志里出现空白 */
    private String resolveOperator(String operator) {
        return StringUtils.hasText(operator) ? operator : SYSTEM_OPERATOR;
    }

    /**
     * 生成订单号：ORD + yyyyMMdd + 3 位当日序号（如 ORD20261004001）。
     * 序号取自 t_seq 表，按天独立计数，并用一条原子语句取值，避免并发下重号。
     */
    private String nextOrderNo(LocalDate date) {
        String day = date.format(DateTimeFormatter.BASIC_ISO_DATE);
        String seqName = "order_no_" + day;
        seqMapper.bump(seqName);
        long next = seqMapper.lastValue();
        return "ORD" + day + String.format("%03d", next);
    }
}
