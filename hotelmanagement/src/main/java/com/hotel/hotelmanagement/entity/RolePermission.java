package com.hotel.hotelmanagement.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 角色-权限关联表实体（RBAC：角色与权限的多对多关系）
 */
@Data
@TableName("t_role_permission")
public class RolePermission {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 角色ID（t_role.id） */
    private Long roleId;

    /** 权限ID（t_permission.id） */
    private Long permissionId;
}
