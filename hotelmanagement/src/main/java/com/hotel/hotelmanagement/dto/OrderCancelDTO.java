package com.hotel.hotelmanagement.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 取消订单参数
 */
@Data
public class OrderCancelDTO {

    @NotNull(message = "订单ID不能为空")
    private Long orderId;

    /** 取消原因（可选；长度上限与 t_order.cancel_reason 保持一致） */
    @Size(max = 255, message = "取消原因长度不能超过255个字符")
    private String reason;
}
