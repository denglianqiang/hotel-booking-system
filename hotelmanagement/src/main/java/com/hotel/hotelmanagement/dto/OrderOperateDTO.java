package com.hotel.hotelmanagement.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 订单操作参数（入住/退房）
 */
@Data
public class OrderOperateDTO {

    @NotNull(message = "订单ID不能为空")
    private Long orderId;
}
