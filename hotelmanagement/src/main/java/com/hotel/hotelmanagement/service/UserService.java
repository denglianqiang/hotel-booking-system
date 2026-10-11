package com.hotel.hotelmanagement.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hotel.hotelmanagement.dto.LoginDTO;
import com.hotel.hotelmanagement.dto.UserDTO;
import com.hotel.hotelmanagement.entity.User;

import java.util.List;
import java.util.Map;

/**
 * 系统用户 Service 接口。
 *
 * <p>RBAC：账号权限由角色授予（用户→角色→权限），
 * 对外仍以「权限码逗号分隔字符串」的形态填充在 {@link User} 上，
 * 拦截器鉴权与前端菜单的契约保持不变。
 */
public interface UserService extends IService<User> {

    /**
     * 统一登录：先匹配管理端账号（admin/operator），再匹配客人账号（手机号），
     * 返回 token、主角色码、昵称及权限码列表
     */
    Map<String, Object> login(LoginDTO dto);

    /**
     * 根据 token 获取当前登录用户信息（不含密码；角色/权限实时联查）
     */
    User getByToken(String token);

    /**
     * 注销登录
     */
    void logout(String token);

    /**
     * 校验当前登录用户持有运营方角色，否则抛出无权异常；返回当前用户
     */
    User requireOperator(String token);

    /**
     * 用户列表（不含密码；角色/权限批量联查填充）
     */
    List<User> listUsers();

    /**
     * 新增管理端账号（roleIds 指定其持有的角色）
     */
    void saveUser(UserDTO dto);

    /**
     * 编辑账号：昵称、角色（roleIds 提供即整体替换）、密码可选重置（用户名不可改）
     */
    void updateUser(UserDTO dto);

    /**
     * 删除账号（持有内置角色的账号不允许删除）
     */
    void deleteUser(Long id);
}
