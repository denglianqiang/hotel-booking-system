package com.hotel.hotelmanagement.service;

import com.hotel.hotelmanagement.dto.RoleDTO;
import com.hotel.hotelmanagement.dto.RoleVO;
import com.hotel.hotelmanagement.entity.Permission;

import java.util.List;

/**
 * 角色与权限管理 Service 接口（RBAC 的「角色-权限」侧）
 */
public interface RoleService {

    /**
     * 角色列表（含每个角色拥有的权限码）
     */
    List<RoleVO> listRoles();

    /**
     * 全部权限码清单（角色编辑对话框的勾选项）
     */
    List<Permission> listPermissions();

    /**
     * 新增角色（编码唯一，同时写入角色-权限关联）
     */
    void saveRole(RoleDTO dto);

    /**
     * 编辑角色（名称/说明/权限集合；编码不可修改）。
     * 权限集合调整后，持有该角色的账号立即按新权限生效。
     */
    void updateRole(RoleDTO dto);

    /**
     * 删除角色（内置角色不允许删除；仍有账号持有时不允许删除）
     */
    void deleteRole(Long id);
}
