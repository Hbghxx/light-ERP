package com.example.erp.service;

import com.example.erp.common.BusinessException;
import com.example.erp.entity.SysRole;
import com.example.erp.entity.SysDept;
import com.example.erp.entity.SysUser;
import com.example.erp.common.UserContext;
import com.example.erp.mapper.SysDeptMapper;
import com.example.erp.mapper.SysRoleMapper;
import com.example.erp.mapper.SysUserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 账号管理：创建/启停/解锁/重置密码/分配角色。
 * 所有变更动作写入审计日志（谁、何时、对谁、做了什么）；ADMIN 账号受保护不可停用/锁定。
 */
@Service
public class UserAdminService {

    @Autowired private SysUserMapper userMapper;
    @Autowired private SysRoleMapper roleMapper;
    @Autowired private SysDeptMapper deptMapper;
    @Autowired private AuthService authService;
    @Autowired private AuditService auditService;

    public List<SysUser> listUsers() {
        return userMapper.listAll();
    }

    public List<SysRole> listRoles() {
        return roleMapper.listAll();
    }

    public List<SysDept> listDepts() {
        return deptMapper.listAll();
    }

    public List<Long> listRoleIds(Long userId) {
        return userMapper.listRoleIds(userId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void create(String username, String password, String realName, Long deptId, List<Long> roleIds) {
        if (username == null || username.isBlank()) {
            throw new BusinessException("用户名不能为空");
        }
        if (userMapper.findByUsername(username.trim()) != null) {
            throw new BusinessException("用户名已存在");
        }
        if (password == null || password.length() < 8) {
            throw new BusinessException("初始密码长度至少 8 位");
        }
        if (deptId == null || deptMapper.findById(deptId) == null) {
            throw new BusinessException("部门不存在");
        }
        if (roleIds == null || roleIds.isEmpty()) {
            throw new BusinessException("至少分配一个角色");
        }
        for (Long roleId : roleIds) {
            if (roleMapper.findById(roleId) == null) {
                throw new BusinessException("角色不存在: " + roleId);
            }
        }
        // 越权防护：非 ADMIN 操作者不得创建 ADMIN 角色账号
        if (roleIds.contains(1L) && !UserContext.get().isAdmin()) {
            throw new BusinessException("无权创建超级管理员账号");
        }

        SysUser user = new SysUser();
        user.setUsername(username.trim());
        user.setPassword(authService.encryptPassword(password));
        user.setRealName(realName);
        user.setDeptId(deptId);
        user.setStatus(1);
        user.setNeedChangePwd(1);
        userMapper.insert(user);
        roleIds.forEach(roleId -> roleMapper.insertUserRole(user.getId(), roleId));

        auditService.write(UserContext.currentUserId(), "CREATE_USER", "USER", user.getId(), null,
                Map.of("username", user.getUsername(), "deptId", deptId, "roleIds", roleIds), null);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long userId, Integer status) {
        if (status == null || status < 0 || status > 2) {
            throw new BusinessException("非法状态值");
        }
        if (userId.equals(UserContext.currentUserId())) {
            throw new BusinessException("不能对自己执行停用/锁定操作");
        }
        SysUser target = requireUser(userId);
        if (hasAdminRole(userId) && status != 1) {
            throw new BusinessException("不允许停用/锁定超级管理员账号");
        }
        if (target.getStatus().equals(status)) {
            return;
        }
        userMapper.updateStatus(userId, status);
        authService.invalidate(userId);
        String action = switch (status) {
            case 0 -> "DISABLE_USER";
            case 2 -> "LOCK_USER";
            default -> target.getStatus() == 2 ? "UNLOCK_USER" : "ENABLE_USER";
        };
        auditService.write(UserContext.currentUserId(), action, "USER", userId, null,
                Map.of("username", target.getUsername(), "from", target.getStatus(), "to", status), null);
    }

    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long userId, String newPassword) {
        SysUser target = requireUser(userId);
        authService.adminResetPassword(userId, newPassword);
        auditService.write(UserContext.currentUserId(), "RESET_PASSWORD", "USER", userId, null,
                Map.of("username", target.getUsername()), null);
    }

    @Transactional(rollbackFor = Exception.class)
    public void assignRoles(Long userId, List<Long> roleIds) {
        SysUser target = requireUser(userId);
        if (roleIds == null || roleIds.isEmpty()) {
            throw new BusinessException("至少保留一个角色");
        }
        for (Long roleId : roleIds) {
            if (roleMapper.findById(roleId) == null) {
                throw new BusinessException("角色不存在: " + roleId);
            }
        }
        List<Long> before = userMapper.listRoleIds(userId);
        // ADMIN 角色不可被移除（保证系统始终存在超管）
        if (before.contains(1L) && !roleIds.contains(1L)) {
            throw new BusinessException("不能移除 ADMIN 角色（系统需保留至少一个超管）");
        }
        roleMapper.deleteUserRoles(userId);
        roleIds.forEach(roleId -> roleMapper.insertUserRole(userId, roleId));
        authService.invalidate(userId);
        // 集合比较（忽略顺序），避免重复提交相同角色产生冗余审计
        List<Long> beforeSorted = new ArrayList<>(before);
        List<Long> afterSorted = new ArrayList<>(roleIds);
        beforeSorted.sort(Long::compareTo);
        afterSorted.sort(Long::compareTo);
        if (!beforeSorted.equals(afterSorted)) {
            auditService.write(UserContext.currentUserId(), "ASSIGN_ROLES", "USER", userId, null,
                    Map.of("username", target.getUsername(), "before", before, "after", roleIds), null);
        }
    }

    private SysUser requireUser(Long userId) {
        SysUser user = userMapper.findById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return user;
    }

    private boolean hasAdminRole(Long userId) {
        return roleMapper.listRoleCodes(userId).contains("ADMIN");
    }
}
