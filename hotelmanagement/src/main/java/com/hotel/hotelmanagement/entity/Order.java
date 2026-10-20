package com.hotel.hotelmanagement.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 订单表实体
 */
@Data
@TableName("t_order")
public class Order {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 订单号 */
    private String orderNo;

    /** 关联房间ID */
    private Long roomId;

    /** 关联客户ID */
    private Long customerId;

    /** 客户姓名（冗余字段） */
    private String customerName;

    /** 入住日期 */
    private LocalDate checkInDate;

    /** 退房日期 */
    private LocalDate checkOutDate;

    /** 总价 */
    private BigDecimal totalPrice;

    /** 状态: 0待支付 1已支付 2已入住 3已退房 4已取消 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 支付时间 */
    private LocalDateTime paidTime;

    /** 待支付截止时间（超时由定时任务自动取消；待支付订单本身不占用房间） */
    private LocalDateTime payDeadline;

    /** 取消时间 */
    private LocalDateTime cancelTime;

    /** 取消原因 */
    private String cancelReason;

    /** 退款金额（待支付订单取消时为 0） */
    private BigDecimal refundAmount;

    /** 实际入住时间 */
    private LocalDateTime actualCheckInTime;

    /** 实际退房时间（收入统计按此时间归属，check_out_date 保留为计划退房日） */
    private LocalDateTime actualCheckOutTime;

    /** 最终结算金额（退房时写入；未退房为空） */
    private BigDecimal finalPrice;

    /** 房间号（非数据库字段，联查时填充） */
    @TableField(exist = false)
    private String roomNumber;
}
