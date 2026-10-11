package com.hotel.hotelmanagement.controller;

import com.hotel.hotelmanagement.common.Result;
import com.hotel.hotelmanagement.dto.LoginDTO;
import com.hotel.hotelmanagement.dto.UserDTO;
import com.hotel.hotelmanagement.entity.User;
import com.hotel.hotelmanagement.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 系统用户（统一登录 + 运营方用户管理） Controller
 */
@RestController
@RequestMapping("/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * POST /user/login - 统一登录（管理端账号与客人账号共用）
     */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@Valid @RequestBody LoginDTO dto) {
        return Result.success("登录成功", userService.login(dto));
    }

    /**
     * GET /user/info - 获取当前登录用户信息
     */
    @GetMapping("/info")
    public Result<User> info(@RequestHeader(value = "Authorization", required = false) String token) {
        User user = userService.getByToken(token);
        if (user == null) {
            return Result.error(401, "未登录或登录已过期");
        }
        return Result.success(user);
    }

    /**
     * POST /user/logout - 注销登录
     */
    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader(value = "Authorization", required = false) String token) {
        userService.logout(token);
        return Result.success("已退出登录");
    }

    // ===== 以下为运营方用户管理接口 =====

    /**
     * GET /user/list - 用户列表
     */
    @GetMapping("/list")
    public Result<List<User>> list(@RequestHeader("Authorization") String token) {
        userService.requireOperator(token);
        return Result.success(userService.listUsers());
    }

    /**
     * POST /user/save - 新增账号
     */
    @PostMapping("/save")
    public Result<Void> save(@RequestHeader("Authorization") String token,
                             @RequestBody UserDTO dto) {
        userService.requireOperator(token);
        userService.saveUser(dto);
        return Result.success("新增成功");
    }

    /**
     * PUT /user/update - 编辑账号（昵称/角色/权限/重置密码）
     */
    @PutMapping("/update")
    public Result<Void> update(@RequestHeader("Authorization") String token,
                               @RequestBody UserDTO dto) {
        userService.requireOperator(token);
        userService.updateUser(dto);
        return Result.success("修改成功");
    }

    /**
     * DELETE /user/delete/{id} - 删除账号
     */
    @DeleteMapping("/delete/{id}")
    public Result<Void> delete(@RequestHeader("Authorization") String token,
                               @PathVariable Long id) {
        userService.requireOperator(token);
        userService.deleteUser(id);
        return Result.success("删除成功");
    }
}