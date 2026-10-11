package com.hotel.hotelmanagement.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 房间表实体
 */
@Data
@TableName("t_room")
public class Room {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 房间号 */
    private String roomNumber;

    /** 房间类型: 单人间/双人间/套房 */
    private String type;

    /** 价格（元/晚） */
    private BigDecimal price;

    /**
     * 可售性状态: 0正常销售 3停用维修。
     * 注意：已订/在住不再落库，由「订单 + 日期区间」推导，见 RoomAvailabilityService。
     */
    private Integer status;

    /** 楼层 */
    private Integer floor;

    /** 房间描述 */
    private String description;

    // ===== 以下为非数据库字段，按日期区间联查时由 RoomAvailabilityService 填充 =====

    /** 派生占用状态: available可订 / booked已订 / occupied在住 / repair维修 */
    @TableField(exist = false)
    private String availability;

    /** 造成占用的订单ID（availability 为 available/repair 时为空） */
    @TableField(exist = false)
    private Long occupierOrderId;

    /** 造成占用的订单号（展示用） */
    @TableField(exist = false)
    private String occupierOrderNo;
}
