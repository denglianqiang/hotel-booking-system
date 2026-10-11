package com.hotel.hotelmanagement.controller;

import com.hotel.hotelmanagement.annotation.RequirePermission;
import com.hotel.hotelmanagement.common.Result;
import com.hotel.hotelmanagement.dto.RoleDTO;
import com.hotel.hotelmanagement.dto.RoleVO;
import com.hotel.hotelmanagement.entity.Permission;
import com.hotel.hotelmanagement.service.RoleService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 角色与权限管理 Controller（RBAC 的「角色-权限」侧）。
 *
 * <p>整个 /role/** 要求登录 + users 权限码（与账号管理同权限，
 * 由 operator 内置角色持有；admin 无 users 权限，符合监管侧治理的设计）。
 */
@RestController
@RequestMapping("/role")
@RequirePermission("users")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    /**
     * GET /role/list - 角色列表（含每个角色拥有的权限码）
     */
    @GetMapping("/list")
    public Result<List<RoleVO>> list() {
        return Result.success(roleService.listRoles());
    }

    /**
     * GET /role/permissions - 全部权限码清单（角色编辑对话框的勾选项）
     */
    @GetMapping("/permissions")
    public Result<List<Permission>> permissions() {
        return Result.success(roleService.listPermissions());
    }

    /**
     * POST /role/save - 新增角色
     */
    @PostMapping("/save")
    public Result<Void> save(@RequestBody RoleDTO dto) {
        roleService.saveRole(dto);
        return Result.success("新增成功");
    }

    /**
     * PUT /role/update - 编辑角色（名称/说明/权限集合；编码不可修改）
     */
    @PutMapping("/update")
    public Result<Void> update(@RequestBody RoleDTO dto) {
        roleService.updateRole(dto);
        return Result.success("修改成功");
    }

    /**
     * DELETE /role/delete/{id} - 删除角色（内置角色/仍被账号持有的角色不允许删除）
     */
    @DeleteMapping("/delete/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        roleService.deleteRole(id);
        return Result.success("删除成功");
    }
}
