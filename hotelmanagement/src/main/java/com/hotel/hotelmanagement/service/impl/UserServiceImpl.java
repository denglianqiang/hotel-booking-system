package com.hotel.hotelmanagement.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hotel.hotelmanagement.common.BusinessException;
import com.hotel.hotelmanagement.dto.LoginDTO;
import com.hotel.hotelmanagement.dto.UserDTO;
import com.hotel.hotelmanagement.entity.Customer;
import com.hotel.hotelmanagement.entity.Permission;
import com.hotel.hotelmanagement.entity.Role;
import com.hotel.hotelmanagement.entity.RolePermission;
import com.hotel.hotelmanagement.entity.User;
import com.hotel.hotelmanagement.entity.UserRole;
import com.hotel.hotelmanagement.mapper.CustomerMapper;
import com.hotel.hotelmanagement.mapper.PermissionMapper;
import com.hotel.hotelmanagement.mapper.RoleMapper;
import com.hotel.hotelmanagement.mapper.RolePermissionMapper;
import com.hotel.hotelmanagement.mapper.UserMapper;
import com.hotel.hotelmanagement.mapper.UserRoleMapper;
import com.hotel.hotelmanagement.service.UserService;
import com.hotel.hotelmanagement.util.PasswordUtil;
import com.hotel.hotelmanagement.util.TokenStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 系统用户 Service 实现类。
 *
 * <p>RBAC：用户通过角色获得权限（用户→角色→权限），
 * {@link #roleCodesOf} / {@link #permCodesOf} 是两条实时联查口径；
 * 权限码最终仍以逗号分隔字符串填充到 {@link User#getPermissions()}，
 * 因此拦截器鉴权与前端菜单的契约保持不变。
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private static final String ROLE_OPERATOR = "operator";

    private final CustomerMapper customerMapper;
    private final TokenStore tokenStore;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final PermissionMapper permissionMapper;

    public UserServiceImpl(CustomerMapper customerMapper, TokenStore tokenStore,
                           RoleMapper roleMapper, UserRoleMapper userRoleMapper,
                           RolePermissionMapper rolePermissionMapper, PermissionMapper permissionMapper) {
        this.customerMapper = customerMapper;
        this.tokenStore = tokenStore;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.permissionMapper = permissionMapper;
    }

    /**
     * 统一登录：管理端账号与客人账号共用一个入口，按匹配结果返回角色
     */
    @Override
    public Map<String, Object> login(LoginDTO dto) {
        // 1. 匹配管理端账号（admin/operator）
        User user = this.getOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, dto.getUsername()));
        if (user != null) {
            if (!PasswordUtil.matches(dto.getPassword(), user.getPassword())) {
                log.warn("登录失败（管理端账号或密码错误）: username={}", dto.getUsername());
                throw new BusinessException("账号或密码错误");
            }
            // 历史 MD5 摘要登录成功后透明升级为 BCrypt
            upgradeLegacyPassword(user, dto.getPassword());
            String token = tokenStore.createToken(user.getId());
            List<String> roleCodes = roleCodesOf(user.getId());
            List<String> permCodes = permCodesOf(user.getId());
            log.info("登录成功: username={}, roles={}, permissions={}",
                    user.getUsername(), roleCodes, permCodes);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("token", token);
            // 主角色取第一个角色码：前端按 role==='operator'/'customer' 判断入口
            result.put("role", roleCodes.isEmpty() ? "" : roleCodes.get(0));
            result.put("nickname", user.getNickname());
            result.put("permissions", String.join(",", permCodes));
            return result;
        }

        // 2. 匹配客人账号（手机号注册，管理员代录的客户无密码不能登录）
        Customer customer = customerMapper.selectOne(new LambdaQueryWrapper<Customer>()
                .eq(Customer::getPhone, dto.getUsername()));
        if (customer == null || customer.getPassword() == null
                || !PasswordUtil.matches(dto.getPassword(), customer.getPassword())) {
            log.warn("登录失败（用户手机号不存在或密码错误）: username={}", dto.getUsername());
            throw new BusinessException("账号或密码错误");
        }
        upgradeLegacyCustomerPassword(customer, dto.getPassword());
        String token = tokenStore.createClientToken(customer.getId());
        log.info("登录成功: 普通用户 phone={}, customerId={}", customer.getPhone(), customer.getId());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("token", token);
        result.put("role", "customer");
        result.put("nickname", customer.getName());
        result.put("customerId", customer.getId());
        return result;
    }

    /**
     * 历史遗留的无盐 MD5 摘要，在登录成功后透明升级为 BCrypt。
     * 这样存量账号无需强制改密，也不会长期停留在弱摘要上。
     */
    private void upgradeLegacyPassword(User user, String rawPassword) {
        if (PasswordUtil.isLegacy(user.getPassword())) {
            user.setPassword(PasswordUtil.encode(rawPassword));
            this.updateById(user);
        }
    }

    /**
     * 客人账号的历史 MD5 摘要同样在登录成功后透明升级
     */
    private void upgradeLegacyCustomerPassword(Customer customer, String rawPassword) {
        if (PasswordUtil.isLegacy(customer.getPassword())) {
            customer.setPassword(PasswordUtil.encode(rawPassword));
            customerMapper.updateById(customer);
        }
    }

    /**
     * 根据 token 获取当前登录用户信息（角色/权限实时联查，供鉴权与展示）
     */
    @Override
    public User getByToken(String token) {
        Long userId = tokenStore.getUserId(token);
        if (userId == null) {
            return null;
        }
        User user = this.getById(userId);
        if (user != null) {
            user.setPassword(null);
            user.setRole(String.join(",", roleCodesOf(userId)));
            user.setPermissions(String.join(",", permCodesOf(userId)));
        }
        return user;
    }

    /**
     * 注销登录。
     * 登录入口是统一的，所以这里不区分管理端/客人端 token，两侧都尝试清理，
     * 避免普通用户账号调用本接口时 token 实际未失效。
     */
    @Override
    public void logout(String token) {
        tokenStore.removeAnyToken(token);
    }

    /**
     * 校验当前登录用户持有运营方角色
     */
    @Override
    public User requireOperator(String token) {
        User user = getByToken(token);
        if (user == null) {
            throw new BusinessException(401, "未登录或登录已过期");
        }
        if (!roleCodesOf(user.getId()).contains(ROLE_OPERATOR)) {
            // 用 403 而不是默认的 500：这是"已登录但无权"，与权限拦截器的口径保持一致
            throw new BusinessException(403, "仅运营方可以管理用户");
        }
        return user;
    }

    /**
     * 用户列表（角色码与权限码批量联查后填充）
     */
    @Override
    public List<User> listUsers() {
        List<User> users = this.list(new LambdaQueryWrapper<User>().orderByAsc(User::getId));
        if (users.isEmpty()) {
            return users;
        }
        users.forEach(u -> u.setPassword(null));

        // 三轮批量查询替代逐用户联查：用户→角色、角色→权限、权限明细，最后内存归并
        List<Long> userIds = users.stream().map(User::getId).collect(Collectors.toList());
        Map<Long, Set<Long>> roleIdsByUser = userRoleMapper.selectList(new LambdaQueryWrapper<UserRole>()
                        .in(UserRole::getUserId, userIds)).stream()
                .collect(Collectors.groupingBy(UserRole::getUserId,
                        Collectors.mapping(UserRole::getRoleId, Collectors.toSet())));
        Set<Long> allRoleIds = roleIdsByUser.values().stream()
                .flatMap(Set::stream).collect(Collectors.toSet());
        Map<Long, Role> roleById = allRoleIds.isEmpty() ? Map.of()
                : roleMapper.selectBatchIds(allRoleIds).stream()
                        .collect(Collectors.toMap(Role::getId, r -> r));
        Map<Long, Set<Long>> permIdsByRole = allRoleIds.isEmpty() ? Map.of()
                : rolePermissionMapper.selectList(new LambdaQueryWrapper<RolePermission>()
                        .in(RolePermission::getRoleId, allRoleIds)).stream()
                .collect(Collectors.groupingBy(RolePermission::getRoleId,
                        Collectors.mapping(RolePermission::getPermissionId, Collectors.toSet())));
        Set<Long> allPermIds = permIdsByRole.values().stream()
                .flatMap(Set::stream).collect(Collectors.toSet());
        Map<Long, Permission> permById = allPermIds.isEmpty() ? Map.of()
                : permissionMapper.selectBatchIds(allPermIds).stream()
                        .collect(Collectors.toMap(Permission::getId, p -> p));

        for (User u : users) {
            List<Permission> perms = roleIdsByUser.getOrDefault(u.getId(), Set.of()).stream()
                    .map(permIdsByRole::get)
                    .filter(permIds -> permIds != null)
                    .flatMap(Set::stream)
                    .map(permById::get)
                    .filter(p -> p != null)
                    .distinct()
                    .sorted(Comparator.comparing(p -> p.getSortOrder() == null ? 0 : p.getSortOrder()))
                    .collect(Collectors.toList());
            u.setPermissions(perms.stream().map(Permission::getPermCode).collect(Collectors.joining(",")));
            u.setRole(roleIdsByUser.getOrDefault(u.getId(), Set.of()).stream()
                    .map(roleById::get)
                    .filter(r -> r != null)
                    .map(Role::getRoleCode)
                    .sorted()
                    .collect(Collectors.joining(",")));
        }
        return users;
    }

    /**
     * 新增管理端账号
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveUser(UserDTO dto) {
        if (!StringUtils.hasText(dto.getUsername())) {
            throw new BusinessException("用户名不能为空");
        }
        if (!StringUtils.hasText(dto.getPassword())) {
            throw new BusinessException("密码不能为空");
        }
        Set<Long> roleIds = validRoleIds(dto.getRoleIds(), true);
        long count = this.count(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, dto.getUsername()));
        if (count > 0) {
            throw new BusinessException("用户名已存在");
        }
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(PasswordUtil.encode(dto.getPassword()));
        user.setNickname(StringUtils.hasText(dto.getNickname()) ? dto.getNickname() : dto.getUsername());
        user.setCreateTime(LocalDateTime.now());
        this.save(user);
        grantRoles(user.getId(), roleIds);
        log.info("新增账号: username={}, roleIds={}", user.getUsername(), roleIds);
    }

    /**
     * 编辑账号：昵称/角色/密码（用户名不可改）。
     * roleIds 传 null 表示不改角色；传了就整体替换。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUser(UserDTO dto) {
        User user = this.getById(dto.getId());
        if (user == null) {
            throw new BusinessException("账号不存在");
        }
        if (StringUtils.hasText(dto.getNickname())) {
            user.setNickname(dto.getNickname());
        }
        if (StringUtils.hasText(dto.getPassword())) {
            user.setPassword(PasswordUtil.encode(dto.getPassword()));
        }
        this.updateById(user);
        if (dto.getRoleIds() != null) {
            Set<Long> roleIds = validRoleIds(dto.getRoleIds(), true);
            userRoleMapper.delete(new LambdaQueryWrapper<UserRole>()
                    .eq(UserRole::getUserId, user.getId()));
            grantRoles(user.getId(), roleIds);
        }
        log.info("编辑账号: id={}, roleIds={}", user.getId(), dto.getRoleIds());
    }

    /**
     * 删除账号。持有内置角色（系统管理员/运营方）的账号不允许删除，
     * 避免误删后失去管理入口；同时清理该账号的角色关联。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteUser(Long id) {
        User user = this.getById(id);
        if (user == null) {
            throw new BusinessException("账号不存在");
        }
        List<Long> roleIds = userRoleMapper.selectList(new LambdaQueryWrapper<UserRole>()
                        .eq(UserRole::getUserId, id)).stream()
                .map(UserRole::getRoleId)
                .collect(Collectors.toList());
        boolean builtIn = roleIds.isEmpty() ? false
                : roleMapper.selectBatchIds(roleIds).stream()
                        .anyMatch(r -> r.getBuiltIn() != null && r.getBuiltIn() == 1);
        if (builtIn) {
            throw new BusinessException("持有内置角色的账号不允许删除");
        }
        userRoleMapper.delete(new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, id));
        this.removeById(id);
        log.info("删除账号: id={}, username={}", id, user.getUsername());
    }

    /**
     * 用户持有的角色码（实时联查）
     */
    private List<String> roleCodesOf(Long userId) {
        return roleMapper.selectRoleCodesByUserId(userId);
    }

    /**
     * 用户拥有的权限码（实时联查：用户→角色→权限）
     */
    private List<String> permCodesOf(Long userId) {
        return permissionMapper.selectPermCodesByUserId(userId);
    }

    /**
     * 校验角色ID集合：非空且全部真实存在，返回去重后的集合
     */
    private Set<Long> validRoleIds(List<Long> roleIds, boolean required) {
        if (roleIds == null || roleIds.isEmpty()) {
            if (required) {
                throw new BusinessException("请至少分配一个角色");
            }
            return Set.of();
        }
        Set<Long> ids = new LinkedHashSet<>(roleIds);
        long found = roleMapper.selectCount(new LambdaQueryWrapper<Role>().in(Role::getId, ids));
        if (found < ids.size()) {
            throw new BusinessException("存在无效的角色，请刷新后重试");
        }
        return ids;
    }

    /**
     * 写入用户-角色关联
     */
    private void grantRoles(Long userId, Set<Long> roleIds) {
        for (Long roleId : roleIds) {
            UserRole link = new UserRole();
            link.setUserId(userId);
            link.setRoleId(roleId);
            userRoleMapper.insert(link);
        }
    }
}
