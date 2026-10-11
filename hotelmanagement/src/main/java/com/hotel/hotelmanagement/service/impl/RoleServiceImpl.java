package com.hotel.hotelmanagement.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hotel.hotelmanagement.common.BusinessException;
import com.hotel.hotelmanagement.dto.RoleDTO;
import com.hotel.hotelmanagement.dto.RoleVO;
import com.hotel.hotelmanagement.entity.Permission;
import com.hotel.hotelmanagement.entity.Role;
import com.hotel.hotelmanagement.entity.RolePermission;
import com.hotel.hotelmanagement.entity.UserRole;
import com.hotel.hotelmanagement.mapper.PermissionMapper;
import com.hotel.hotelmanagement.mapper.RoleMapper;
import com.hotel.hotelmanagement.mapper.RolePermissionMapper;
import com.hotel.hotelmanagement.mapper.UserRoleMapper;
import com.hotel.hotelmanagement.service.RoleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 角色与权限管理 Service 实现类（RBAC 的「角色-权限」侧）
 */
@Slf4j
@Service
public class RoleServiceImpl implements RoleService {

    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;
    private final UserRoleMapper userRoleMapper;
    private final RolePermissionMapper rolePermissionMapper;

    public RoleServiceImpl(RoleMapper roleMapper, PermissionMapper permissionMapper,
                           UserRoleMapper userRoleMapper, RolePermissionMapper rolePermissionMapper) {
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
        this.userRoleMapper = userRoleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
    }

    @Override
    public List<RoleVO> listRoles() {
        List<Role> roles = roleMapper.selectList(
                new LambdaQueryWrapper<Role>().orderByAsc(Role::getId));
        if (roles.isEmpty()) {
            return List.of();
        }

        // 一次取全量关联与权限，内存归并成 RoleVO（角色数量级很小，无需分页）
        List<RolePermission> links = rolePermissionMapper.selectList(null);
        Map<Long, Set<Long>> permIdsByRole = links.stream()
                .collect(Collectors.groupingBy(RolePermission::getRoleId,
                        Collectors.mapping(RolePermission::getPermissionId, Collectors.toSet())));
        Map<Long, Permission> permById = permissionMapper.selectList(null).stream()
                .collect(Collectors.toMap(Permission::getId, Function.identity()));

        return roles.stream().map(role -> {
            RoleVO vo = new RoleVO();
            vo.setId(role.getId());
            vo.setRoleCode(role.getRoleCode());
            vo.setRoleName(role.getRoleName());
            vo.setDescription(role.getDescription());
            vo.setBuiltIn(role.getBuiltIn());
            // 权限码按展示排序输出，前端勾选/展示顺序稳定
            vo.setPermissionCodes(permIdsByRole.getOrDefault(role.getId(), Set.of()).stream()
                    .map(permById::get)
                    .filter(p -> p != null)
                    .sorted(Comparator.comparing(p -> p.getSortOrder() == null ? 0 : p.getSortOrder()))
                    .map(Permission::getPermCode)
                    .collect(Collectors.toList()));
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public List<Permission> listPermissions() {
        return permissionMapper.selectList(new LambdaQueryWrapper<Permission>()
                .orderByAsc(Permission::getSortOrder)
                .orderByAsc(Permission::getId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveRole(RoleDTO dto) {
        if (!StringUtils.hasText(dto.getRoleCode())) {
            throw new BusinessException("角色编码不能为空");
        }
        if (!StringUtils.hasText(dto.getRoleName())) {
            throw new BusinessException("角色名称不能为空");
        }
        Set<String> codes = validPermCodes(dto.getPermissionCodes());

        long count = roleMapper.selectCount(new LambdaQueryWrapper<Role>()
                .eq(Role::getRoleCode, dto.getRoleCode()));
        if (count > 0) {
            throw new BusinessException("角色编码已存在");
        }

        Role role = new Role();
        role.setRoleCode(dto.getRoleCode().trim());
        role.setRoleName(dto.getRoleName().trim());
        role.setDescription(dto.getDescription());
        role.setBuiltIn(0);
        roleMapper.insert(role);
        grantPermissions(role.getId(), codes);
        log.info("新增角色: code={}, name={}, permissions={}", role.getRoleCode(), role.getRoleName(), codes);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRole(RoleDTO dto) {
        if (dto.getId() == null) {
            throw new BusinessException("缺少角色ID");
        }
        Role role = roleMapper.selectById(dto.getId());
        if (role == null) {
            throw new BusinessException("角色不存在");
        }
        if (StringUtils.hasText(dto.getRoleCode()) && !dto.getRoleCode().equals(role.getRoleCode())) {
            // 编码是账号授权与前端菜单的关联键，修改会让已发放的权限口径漂移，直接禁止
            throw new BusinessException("角色编码不可修改");
        }
        if (!StringUtils.hasText(dto.getRoleName())) {
            throw new BusinessException("角色名称不能为空");
        }
        Set<String> codes = validPermCodes(dto.getPermissionCodes());

        role.setRoleName(dto.getRoleName().trim());
        role.setDescription(dto.getDescription());
        roleMapper.updateById(role);
        // 关联整体替换：调整后持有该角色的账号立即按新权限生效（鉴权为实时联查）
        rolePermissionMapper.delete(new LambdaQueryWrapper<RolePermission>()
                .eq(RolePermission::getRoleId, role.getId()));
        grantPermissions(role.getId(), codes);
        log.info("编辑角色: id={}, code={}, permissions={}", role.getId(), role.getRoleCode(), codes);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRole(Long id) {
        Role role = roleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException("角色不存在");
        }
        if (role.getBuiltIn() != null && role.getBuiltIn() == 1) {
            throw new BusinessException("内置角色不允许删除");
        }
        long users = userRoleMapper.selectCount(new LambdaQueryWrapper<UserRole>()
                .eq(UserRole::getRoleId, id));
        if (users > 0) {
            throw new BusinessException("仍有 " + users + " 个账号持有该角色，请先在账号管理中解除关联");
        }
        rolePermissionMapper.delete(new LambdaQueryWrapper<RolePermission>()
                .eq(RolePermission::getRoleId, id));
        roleMapper.deleteById(id);
        log.info("删除角色: id={}, code={}", id, role.getRoleCode());
    }

    /**
     * 校验权限码合法（必须都已登记在 t_permission），返回去重后的集合
     */
    private Set<String> validPermCodes(List<String> permissionCodes) {
        if (permissionCodes == null || permissionCodes.isEmpty()) {
            throw new BusinessException("请至少勾选一个权限");
        }
        Set<String> codes = new LinkedHashSet<>(permissionCodes);
        List<Permission> perms = permissionMapper.selectList(new LambdaQueryWrapper<Permission>()
                .in(Permission::getPermCode, codes));
        if (perms.size() < codes.size()) {
            throw new BusinessException("存在未登记的权限码，请刷新后重试");
        }
        return codes;
    }

    /**
     * 写入角色-权限关联
     */
    private void grantPermissions(Long roleId, Set<String> codes) {
        if (codes.isEmpty()) {
            return;
        }
        List<Permission> perms = permissionMapper.selectList(new LambdaQueryWrapper<Permission>()
                .in(Permission::getPermCode, codes));
        for (Permission perm : perms) {
            RolePermission link = new RolePermission();
            link.setRoleId(roleId);
            link.setPermissionId(perm.getId());
            rolePermissionMapper.insert(link);
        }
    }
}
