package com.hotel.hotelmanagement.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 客人登录参数（手机号 + 密码）
 */
@Data
public class ClientLoginDTO {

    @NotBlank(message = "手机号不能为空")
    private String phone;

    @NotBlank(message = "密码不能为空")
    private String password;
}
