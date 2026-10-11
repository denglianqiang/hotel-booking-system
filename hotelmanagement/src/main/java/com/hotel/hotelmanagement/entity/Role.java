package com.hotel.hotelmanagement.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色表实体（RBAC：用户-角色-权限 三级中的「角色」层）
 */
@Data
@TableName("t_role")
public class Role {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 角色编码（如 admin/operator），登录响应与展示用 */
    private String roleCode;

    /** 角色名称（页面显示） */
    private String roleName;

    /** 角色说明 */
    private String description;

    /** 是否内置角色: 1内置（不允许删除） 0自定义 */
    private Integer builtIn;

    /** 创建时间 */
    private LocalDateTime createTime;
}
