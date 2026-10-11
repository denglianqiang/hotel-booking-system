package com.hotel.hotelmanagement.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 入住记录表实体
 */
@Data
@TableName("t_check_in")
public class CheckIn {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联订单ID */
    private Long orderId;

    /** 关联房间ID */
    private Long roomId;

    /** 关联客户ID */
    private Long customerId;

    /** 实际入住时间 */
    private LocalDateTime checkInTime;

    /** 实际退房时间 */
    private LocalDateTime checkOutTime;

    /** 实际支付金额 */
    private BigDecimal actualPrice;

    /** 操作人 */
    private String operator;

    // ===== 以下为非数据库字段，查询时联查填充，便于入住登记列表直接展示 =====

    /** 房间号（非数据库字段） */
    @TableField(exist = false)
    private String roomNumber;

    /** 客户姓名（非数据库字段） */
    @TableField(exist = false)
    private String customerName;
}
