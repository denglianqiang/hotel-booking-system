package com.hotel.hotelmanagement.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 房间新增/编辑参数
 *
 * <p>长度上限与建表语句保持一致（room_number/type 20、description 255），
 * 否则超长字符串会一路穿过校验、直到 MySQL 报 "Data too long"，
 * 对外只能返回一个没有信息量的"系统繁忙"。
 */
@Data
public class RoomDTO {

    private Long id;

    @NotBlank(message = "房间号不能为空")
    @Size(max = 20, message = "房间号长度不能超过20个字符")
    private String roomNumber;

    @NotBlank(message = "房间类型不能为空")
    @Size(max = 20, message = "房间类型长度不能超过20个字符")
    private String type;

    /** 房价必须大于 0：0 元房会直接扭曲收入统计与出租率口径 */
    @NotNull(message = "价格不能为空")
    @DecimalMin(value = "0.01", message = "价格必须大于0")
    private BigDecimal price;

    @NotNull(message = "楼层不能为空")
    @Min(value = 1, message = "楼层不能小于1")
    private Integer floor;

    @Size(max = 255, message = "房间描述长度不能超过255个字符")
    private String description;

    /** 可售性: 0正常销售 3停用维修（仅编辑时可传入） */
    private Integer status;
}
