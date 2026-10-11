package com.hotel.hotelmanagement.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 用户-角色关联表实体（RBAC：用户与角色的多对多关系）
 */
@Data
@TableName("t_user_role")
public class UserRole {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID（t_user.id） */
    private Long userId;

    /** 角色ID（t_role.id） */
    private Long roleId;
}
