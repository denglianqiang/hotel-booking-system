package com.hotel.hotelmanagement.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 房间分页查询参数
 */
@Data
public class RoomQueryDTO {

    /** 当前页码（默认1） */
    @Min(value = 1, message = "页码不能小于1")
    private Integer current = 1;

    /** 每页条数（默认10） */
    @Min(value = 1, message = "每页条数不能小于1")
    @Max(value = 100, message = "每页条数不能超过100")
    private Integer size = 10;

    /** 房间类型（可选筛选） */
    private String type;

    /** 房间状态（可选筛选） */
    private Integer status;

    /** 楼层（可选筛选） */
    private Integer floor;
}
