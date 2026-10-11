package com.hotel.hotelmanagement.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 客人端下单参数（客户身份取自 token，不接收 customerId）
 */
@Data
public class ClientBookDTO {

    @NotNull(message = "房间ID不能为空")
    private Long roomId;

    @NotNull(message = "入住日期不能为空")
    private LocalDate checkInDate;

    @NotNull(message = "退房日期不能为空")
    private LocalDate checkOutDate;
}
