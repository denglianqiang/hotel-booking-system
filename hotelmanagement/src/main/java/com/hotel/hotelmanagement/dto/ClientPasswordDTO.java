package com.hotel.hotelmanagement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 普通用户修改密码参数（密码规则与注册保持一致：6-20 位）
 */
@Data
public class ClientPasswordDTO {

    @NotBlank(message = "原密码不能为空")
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 20, message = "新密码长度需在 6 到 20 位之间")
    private String newPassword;
}
