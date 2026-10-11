package com.hotel.hotelmanagement.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统用户表实体（管理员账号）。
 *
 * <p>角色与权限不落在用户行上（RBAC）：由 t_user_role / t_role_permission
 * 关联表实时联查后填充到 {@link #role} / {@link #permissions}，
 * 因此这两个字段是查询视图字段，不是数据库列。
 */
@Data
@TableName("t_user")
public class User {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户名 */
    private String username;

    /** 密码（BCrypt 摘要，60 位；历史无盐 MD5 摘要仍可校验并在登录时自动升级） */
    private String password;

    /** 昵称（页面显示用） */
    private String nickname;

    /** 角色码集合，逗号分隔（联查填充，非库字段） */
    @TableField(exist = false)
    private String role;

    /** 权限码列表，逗号分隔（联查填充，非库字段，如 roomStatus,rooms,orders） */
    @TableField(exist = false)
    private String permissions;

    /** 创建时间 */
    private LocalDateTime createTime;
}
