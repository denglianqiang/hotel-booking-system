package com.hotel.hotelmanagement.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hotel.hotelmanagement.entity.Role;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 角色表 Mapper 接口
 */
public interface RoleMapper extends BaseMapper<Role> {

    /**
     * 查询用户拥有的角色编码（按角色ID排序，保证展示稳定）。
     * 用 GROUP BY 而非 DISTINCT 去重：MySQL 严格模式不允许 DISTINCT + 按未选列排序。
     */
    @Select("SELECT r.role_code FROM t_role r "
            + "JOIN t_user_role ur ON ur.role_id = r.id "
            + "WHERE ur.user_id = #{userId} "
            + "GROUP BY r.id, r.role_code ORDER BY r.id")
    List<String> selectRoleCodesByUserId(@Param("userId") Long userId);
}
