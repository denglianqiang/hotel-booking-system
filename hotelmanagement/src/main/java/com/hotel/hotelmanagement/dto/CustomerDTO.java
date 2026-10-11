package com.hotel.hotelmanagement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 客户新增/编辑参数
 *
 * <p>校验口径与客人自助注册（ClientRegisterDTO）保持一致：
 * 手机号同样校验格式，并额外对可选的身份证号做 18 位格式校验。
 */
@Data
public class CustomerDTO {

    private Long id;

    @NotBlank(message = "姓名不能为空")
    @Size(max = 50, message = "姓名长度不能超过50个字符")
    private String name;

    @NotBlank(message = "电话不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    /** 身份证号（可选；填写则必须是 18 位，末位可为 X） */
    @Pattern(regexp = "^$|^[1-9]\\d{5}(18|19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[\\dXx]$",
            message = "身份证号格式不正确（应为18位）")
    private String idCard;

    /** 性别（可选） */
    @Pattern(regexp = "^$|^[男女]$", message = "性别只能是男或女")
    private String gender;
}
