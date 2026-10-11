package com.hotel.hotelmanagement.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hotel.hotelmanagement.annotation.RequirePermission;
import com.hotel.hotelmanagement.common.Result;
import com.hotel.hotelmanagement.dto.OrderCancelDTO;
import com.hotel.hotelmanagement.dto.OrderCreateDTO;
import com.hotel.hotelmanagement.dto.OrderOperateDTO;
import com.hotel.hotelmanagement.dto.OrderQueryDTO;
import com.hotel.hotelmanagement.entity.Order;
import com.hotel.hotelmanagement.entity.OrderLog;
import com.hotel.hotelmanagement.entity.User;
import com.hotel.hotelmanagement.service.OrderService;
import com.hotel.hotelmanagement.service.UserService;
import jakarta.validation.Valid;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 订单管理 Controller
 *
 * <p>下单接口同时放行 roomStatus：房态图上点击空房即可下单，
 * 这是前台的核心操作路径，不应要求额外的"订单管理"权限。
 */
@RestController
@RequestMapping("/order")
public class OrderController {

    private final OrderService orderService;
    private final UserService userService;

    public OrderController(OrderService orderService, UserService userService) {
        this.orderService = orderService;
        this.userService = userService;
    }

    /**
     * POST /order/list - 分页查询订单列表
     */
    @PostMapping("/list")
    @RequirePermission("orders")
    public Result<IPage<Order>> list(@Valid @RequestBody OrderQueryDTO query) {
        IPage<Order> page = orderService.queryPage(query);
        return Result.success(page);
    }

    /**
     * POST /order/create - 创建预订订单（房态图与订单页都可发起）
     */
    @PostMapping("/create")
    @RequirePermission({"orders", "roomStatus"})
    public Result<Order> create(@Valid @RequestBody OrderCreateDTO dto) {
        Order order = orderService.createOrder(dto);
        return Result.success("预订成功", order);
    }

    /**
     * POST /order/checkin - 办理入住
     */
    @PostMapping("/checkin")
    @RequirePermission("orders")
    public Result<Void> checkIn(@Valid @RequestBody OrderOperateDTO dto,
                                @RequestHeader(value = "Authorization", required = false) String token) {
        orderService.checkIn(dto, currentOperator(token));
        return Result.success();
    }

    /**
     * POST /order/checkout - 办理退房
     */
    @PostMapping("/checkout")
    @RequirePermission("orders")
    public Result<Void> checkOut(@Valid @RequestBody OrderOperateDTO dto,
                                 @RequestHeader(value = "Authorization", required = false) String token) {
        orderService.checkOut(dto, currentOperator(token));
        return Result.success();
    }

    /**
     * POST /order/pay - 支付订单，返回支付后的订单
     */
    @PostMapping("/pay")
    @RequirePermission("orders")
    public Result<Order> pay(@Valid @RequestBody OrderOperateDTO dto,
                             @RequestHeader(value = "Authorization", required = false) String token) {
        Order order = orderService.payOrder(dto.getOrderId(), currentOperator(token));
        return Result.success("支付成功", order);
    }

    /**
     * POST /order/cancel - 取消订单（待支付或已支付），返回含退款金额的订单
     */
    @PostMapping("/cancel")
    @RequirePermission("orders")
    public Result<Order> cancel(@Valid @RequestBody OrderCancelDTO dto,
                                @RequestHeader(value = "Authorization", required = false) String token) {
        Order order = orderService.cancelOrder(dto.getOrderId(), currentOperator(token), dto.getReason());
        String message = order.getRefundAmount() != null && order.getRefundAmount().signum() > 0
                ? "取消成功，退款 ¥" + order.getRefundAmount()
                : "取消成功";
        return Result.success(message, order);
    }

    /**
     * GET /order/log/{orderId} - 查询订单操作轨迹
     */
    @GetMapping("/log/{orderId}")
    @RequirePermission({"orders", "roomStatus"})
    public Result<List<OrderLog>> logs(@PathVariable Long orderId) {
        return Result.success(orderService.listLogs(orderId));
    }

    /**
     * 从 token 解析当前操作人显示名（昵称优先，其次用户名）
     */
    private String currentOperator(String token) {
        User user = userService.getByToken(token);
        if (user == null) {
            return "系统";
        }
        return StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername();
    }
}
