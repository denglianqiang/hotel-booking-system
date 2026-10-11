package com.hotel.hotelmanagement.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单操作日志实体
 *
 * <p>每一次订单状态流转（下单/支付/取消/入住/退房/超时自动取消）都写一条记录，
 * 解决改造前"支付、取消、入退房完全没有留痕"的问题。
 */
@Data
@TableName("t_order_log")
public class OrderLog {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联订单ID */
    private Long orderId;

    /** 变更前状态（下单时为 null） */
    private Integer fromStatus;

    /** 变更后状态 */
    private Integer toStatus;

    /** 操作人（管理端为账号昵称，客人端为客户姓名，系统动作为"系统"） */
    private String operator;

    /** 备注 */
    private String remark;

    /** 操作时间 */
    private LocalDateTime createTime;
}
