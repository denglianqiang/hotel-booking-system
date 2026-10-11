package com.hotel.hotelmanagement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 管理端账号新增/编辑参数（运营方使用）。
 *
 * <p>RBAC：账号的权限不再直接指定，而是通过分配角色获得
 * （用户→角色→权限），前端「可访问页面」由所选角色的权限并集决定。
 */
@Data
public class UserDTO {

    private Long id;

    /** 用户名（仅新增时使用，不可修改） */
    private String username;

    /** 密码：新增必填，编辑时可选（填写即重置） */
    private String password;

    private String nickname;

    /** 分配的角色ID集合（新增必填；编辑时提供即整体替换） */
    private List<Long> roleIds;
}
