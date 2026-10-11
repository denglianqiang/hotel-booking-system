package com.hotel.hotelmanagement.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 权限表实体（RBAC：用户-角色-权限 三级中的「权限」层）。
 *
 * <p>一个权限码对应一组页面/接口：码与前端 ADMIN_PAGES 一一对应，
 * 后端 @RequirePermission 注解按码校验。
 */
@Data
@TableName("t_permission")
public class Permission {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 权限码（如 rooms/orders） */
    private String permCode;

    /** 权限名称（页面显示） */
    private String permName;

    /** 展示排序 */
    private Integer sortOrder;

    /** 创建时间 */
    private LocalDateTime createTime;
}
