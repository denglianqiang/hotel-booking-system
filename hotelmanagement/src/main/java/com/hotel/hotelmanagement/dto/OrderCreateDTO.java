package com.hotel.hotelmanagement.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 创建预订订单参数
 */
@Data
public class OrderCreateDTO {

    @NotNull(message = "房间ID不能为空")
    private Long roomId;

    @NotNull(message = "客户ID不能为空")
    private Long customerId;

    @NotNull(message = "入住日期不能为空")
    private LocalDate checkInDate;

    @NotNull(message = "退房日期不能为空")
    private LocalDate checkOutDate;
}
