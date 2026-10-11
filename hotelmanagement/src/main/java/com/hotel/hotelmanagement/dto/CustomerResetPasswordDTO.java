package com.hotel.hotelmanagement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理端重置普通用户登录密码参数（密码规则与注册保持一致：6-20 位）
 */
@Data
public class CustomerResetPasswordDTO {

    @NotNull(message = "客户ID不能为空")
    private Long id;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 20, message = "密码长度需在 6 到 20 位之间")
    private String password;
}
