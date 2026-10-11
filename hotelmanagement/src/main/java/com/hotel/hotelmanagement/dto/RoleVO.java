package com.hotel.hotelmanagement.dto;

import lombok.Data;

import java.util.List;

/**
 * 角色视图对象：角色信息 + 拥有的权限码（角色管理列表展示/编辑回填用）
 */
@Data
public class RoleVO {

    private Long id;

    private String roleCode;

    private String roleName;

    private String description;

    /** 是否内置角色: 1内置（不允许删除） 0自定义 */
    private Integer builtIn;

    /** 拥有的权限码集合（按权限展示排序） */
    private List<String> permissionCodes;
}
