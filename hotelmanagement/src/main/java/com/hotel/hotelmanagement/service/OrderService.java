package com.hotel.hotelmanagement.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.hotel.hotelmanagement.dto.OrderCreateDTO;
import com.hotel.hotelmanagement.dto.OrderOperateDTO;
import com.hotel.hotelmanagement.dto.OrderQueryDTO;
import com.hotel.hotelmanagement.entity.Order;
import com.hotel.hotelmanagement.entity.OrderLog;

import java.util.List;

/**
 * 订单表 Service 接口
 *
 * <p>状态机：0待支付 → 1已支付 → 2已入住 → 3已退房；0/1 可取消为 4已取消。
 * 每次流转都会写入 t_order_log 操作日志。
 */
public interface OrderService extends IService<Order> {

    /**
     * 创建预订订单（待支付，含支付截止时间）
     */
    Order createOrder(OrderCreateDTO dto);

    /**
     * 办理入住：写入实际入住时间并生成入住登记记录
     *
     * @param operator 操作人（用于订单日志与入住登记）
     */
    void checkIn(OrderOperateDTO dto, String operator);

    /**
     * 办理退房：写入实际退房时间与结算金额，回填入住登记记录。
     * 计划退房日不变，实际退房时间单独记录。
     */
    void checkOut(OrderOperateDTO dto, String operator);

    /**
     * 分页查询订单列表（含房间号联查）
     */
    IPage<Order> queryPage(OrderQueryDTO query);

    /**
     * 支付订单（0待支付 → 1已支付）。
     * 支付前会重新校验房态（锁定房间行并排除自身），避免下单时不冲突、
     * 支付时该房间已被其他订单抢先占用的超卖场景。
     */
    Order payOrder(Long orderId, String operator);

    /**
     * 取消订单（0待支付 / 1已支付 → 4已取消）。
     * 已支付订单按退款规则计算退款金额并记录；已入住订单不允许直接取消。
     *
     * @param reason 取消原因，可为空
     */
    Order cancelOrder(Long orderId, String operator, String reason);

    /**
     * 查询订单的操作轨迹（按时间倒序）
     */
    List<OrderLog> listLogs(Long orderId);

    /**
     * 在住客人列表（订单状态=已入住），并填充房间号。
     * 用于"入住登记"页的在住客人视图 —— 在住的判断依据是订单状态，
     * 而不是 t_check_in（登记记录只在入住/退房时写入，无法表达"当前在住"）。
     */
    List<Order> listInHouse();

    /**
     * 定时任务：把超时未支付的订单自动取消，释放房间占用
     *
     * @return 本次取消的订单数
     */
    int autoCancelExpiredOrders();
}
