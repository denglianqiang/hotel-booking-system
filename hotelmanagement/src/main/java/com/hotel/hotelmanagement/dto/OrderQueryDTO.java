package com.hotel.hotelmanagement.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 订单分页查询参数
 *
 * <p>分页上限与 RoomQueryDTO 保持一致，避免被传入超大 size 一次性拖库。
 */
@Data
public class OrderQueryDTO {

    @Min(value = 1, message = "页码不能小于1")
    private Integer current = 1;

    @Min(value = 1, message = "每页条数不能小于1")
    @Max(value = 100, message = "每页条数不能超过100")
    private Integer size = 10;

    private String orderNo;

    private String customerName;

    private Integer status;
}
