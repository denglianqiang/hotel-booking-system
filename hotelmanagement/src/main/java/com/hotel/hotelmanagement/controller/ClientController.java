package com.hotel.hotelmanagement.controller;

import com.hotel.hotelmanagement.common.Result;
import com.hotel.hotelmanagement.dto.ClientBookDTO;
import com.hotel.hotelmanagement.dto.ClientLoginDTO;
import com.hotel.hotelmanagement.dto.ClientPasswordDTO;
import com.hotel.hotelmanagement.dto.ClientProfileDTO;
import com.hotel.hotelmanagement.dto.ClientRegisterDTO;
import com.hotel.hotelmanagement.dto.OrderCancelDTO;
import com.hotel.hotelmanagement.dto.OrderOperateDTO;
import com.hotel.hotelmanagement.entity.Customer;
import com.hotel.hotelmanagement.entity.Order;
import com.hotel.hotelmanagement.entity.Room;
import com.hotel.hotelmanagement.service.ClientService;
import com.hotel.hotelmanagement.util.TokenStore;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 客人端（自助预订） Controller
 */
@RestController
@RequestMapping("/client")
public class ClientController {

    private final ClientService clientService;
    private final TokenStore tokenStore;

    public ClientController(ClientService clientService, TokenStore tokenStore) {
        this.clientService = clientService;
        this.tokenStore = tokenStore;
    }

    /**
     * POST /client/register - 客人注册
     */
    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody ClientRegisterDTO dto) {
        clientService.register(dto);
        return Result.success("注册成功，请登录");
    }

    /**
     * POST /client/login - 客人登录
     */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@Valid @RequestBody ClientLoginDTO dto) {
        return Result.success("登录成功", clientService.login(dto));
    }

    /**
     * GET /client/info - 当前客人信息
     */
    @GetMapping("/info")
    public Result<Customer> info(@RequestHeader(value = "Authorization", required = false) String token) {
        Customer customer = clientService.getByToken(token);
        if (customer == null) {
            return Result.error(401, "未登录或登录已过期");
        }
        return Result.success(customer);
    }

    /**
     * POST /client/logout - 注销登录
     */
    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader(value = "Authorization", required = false) String token) {
        clientService.logout(token);
        return Result.success("已退出登录");
    }

    /**
     * PUT /client/profile - 普通用户修改姓名，返回更新后的个人信息（密码字段不外泄）
     */
    @PutMapping("/profile")
    public Result<Customer> updateProfile(@RequestHeader("Authorization") String token,
                                          @Valid @RequestBody ClientProfileDTO dto) {
        return Result.success("资料已更新", clientService.updateProfile(resolveCustomerId(token), dto.getName()));
    }

    /**
     * PUT /client/password - 普通用户修改密码（需校验原密码）
     */
    @PutMapping("/password")
    public Result<Void> changePassword(@RequestHeader("Authorization") String token,
                                       @Valid @RequestBody ClientPasswordDTO dto) {
        clientService.changePassword(resolveCustomerId(token), dto.getOldPassword(), dto.getNewPassword());
        return Result.success("密码修改成功，下次登录请使用新密码");
    }

    /**
     * GET /client/rooms - 客房列表（不含停用维修），
     * 可传 checkInDate / checkOutDate 指定入住区间，不传默认「今天 → 明天」；
     * 每个房间带上派生占用状态 availability（available/booked/occupied）
     */
    @GetMapping("/rooms")
    public Result<List<Room>> rooms(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkInDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOutDate) {
        return Result.success(clientService.listBookableRooms(checkInDate, checkOutDate));
    }

    /**
     * POST /client/order - 客人下单预订
     */
    @PostMapping("/order")
    public Result<Order> book(@RequestHeader("Authorization") String token,
                              @Valid @RequestBody ClientBookDTO dto) {
        Long customerId = resolveCustomerId(token);
        Order order = clientService.book(customerId, dto);
        return Result.success("预订成功", order);
    }

    /**
     * GET /client/orders - 我的订单列表
     */
    @GetMapping("/orders")
    public Result<List<Order>> orders(@RequestHeader("Authorization") String token) {
        return Result.success(clientService.myOrders(resolveCustomerId(token)));
    }

    /**
     * POST /client/order/pay - 支付自己的订单，返回支付后的订单
     */
    @PostMapping("/order/pay")
    public Result<Order> pay(@RequestHeader("Authorization") String token,
                             @Valid @RequestBody OrderOperateDTO dto) {
        Order order = clientService.payOwn(resolveCustomerId(token), dto.getOrderId());
        return Result.success("支付成功", order);
    }

    /**
     * POST /client/order/cancel - 取消自己的订单（待支付或已支付），返回含退款金额的订单
     */
    @PostMapping("/order/cancel")
    public Result<Order> cancel(@RequestHeader("Authorization") String token,
                                @Valid @RequestBody OrderCancelDTO dto) {
        Order order = clientService.cancelOwn(resolveCustomerId(token), dto.getOrderId(), dto.getReason());
        String message = order.getRefundAmount() != null && order.getRefundAmount().signum() > 0
                ? "取消成功，退款 ¥" + order.getRefundAmount()
                : "取消成功";
        return Result.success(message, order);
    }

    /**
     * 从 token 解析客户ID，无效时抛 401 业务异常
     */
    private Long resolveCustomerId(String token) {
        Long customerId = tokenStore.getClientUserId(token);
        if (customerId == null) {
            throw new com.hotel.hotelmanagement.common.BusinessException(401, "未登录或登录已过期");
        }
        return customerId;
    }
}
