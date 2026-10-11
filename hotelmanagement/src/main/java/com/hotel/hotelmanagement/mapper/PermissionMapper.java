package com.hotel.hotelmanagement.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hotel.hotelmanagement.entity.Permission;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 权限表 Mapper 接口
 */
public interface PermissionMapper extends BaseMapper<Permission> {

    /**
     * 查询用户拥有的权限码：经 用户→角色→权限 三表联查（RBAC 核心查询）。
     *
     * <p>GROUP BY 去重：多个角色共享同一权限时只保留一份
     * （MySQL 严格模式不允许 DISTINCT + 按未选列排序，故不用 DISTINCT）。
     * 每次鉴权实时联查，角色/权限调整立即生效；当前请求量级无需缓存。
     */
    @Select("SELECT p.perm_code FROM t_permission p "
            + "JOIN t_role_permission rp ON rp.permission_id = p.id "
            + "JOIN t_user_role ur ON ur.role_id = rp.role_id "
            + "WHERE ur.user_id = #{userId} "
            + "GROUP BY p.id, p.perm_code, p.sort_order ORDER BY p.sort_order, p.id")
    List<String> selectPermCodesByUserId(@Param("userId") Long userId);
}
