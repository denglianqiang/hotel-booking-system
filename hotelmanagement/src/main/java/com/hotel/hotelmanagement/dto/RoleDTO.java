package com.hotel.hotelmanagement.dto;

import lombok.Data;

import java.util.List;

/**
 * 角色新增/编辑参数（账号管理页的「角色管理」使用）
 */
@Data
public class RoleDTO {

    /** 角色ID（编辑时必填） */
    private Long id;

    /** 角色编码（仅新增时可指定，不可修改） */
    private String roleCode;

    /** 角色名称 */
    private String roleName;

    /** 角色说明 */
    private String description;

    /** 授予的权限码集合 */
    private List<String> permissionCodes;
}
