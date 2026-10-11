package com.hotel.hotelmanagement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 普通用户修改个人资料参数。
 *
 * <p>手机号是登录账号且全库唯一，不开放用户自助修改；
 * 证件号等入住信息由前台核实后录入，用户端只读展示。
 */
@Data
public class ClientProfileDTO {

    @NotBlank(message = "姓名不能为空")
    @Size(max = 50, message = "姓名不能超过 50 个字符")
    private String name;
}
