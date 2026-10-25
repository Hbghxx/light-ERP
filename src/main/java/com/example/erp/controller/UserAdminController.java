package com.example.erp.controller;

import com.example.erp.common.RequiresPermission;
import com.example.erp.common.Result;
import com.example.erp.entity.SysAuditLog;
import com.example.erp.entity.SysDept;
import com.example.erp.entity.SysRole;
import com.example.erp.entity.SysUser;
import com.example.erp.service.AuditService;
import com.example.erp.service.UserAdminService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 账号管理：账号 CRUD / 角色分配 / 审计日志查询。
 * 账号类操作要求 user:manage，角色分配额外要求 role:assign，审计查看要求 audit:view。
 */
@RestController
public class UserAdminController {

    @Autowired private UserAdminService userAdminService;
    @Autowired private AuditService auditService;

    @GetMapping("/user/list")
    @RequiresPermission("user:manage")
    public Result<List<SysUser>> listUsers() {
        return Result.ok(userAdminService.listUsers());
    }

    @GetMapping("/user/{id}/roles")
    @RequiresPermission("user:manage")
    public Result<List<Long>> listUserRoles(@PathVariable Long id) {
        return Result.ok(userAdminService.listRoleIds(id));
    }

    @PostMapping("/user/create")
    @RequiresPermission("user:manage")
    public Result<String> create(@RequestParam String username,
                                 @RequestParam String password,
                                 @RequestParam(required = false) String realName,
                                 @RequestParam Long deptId,
                                 @RequestParam List<Long> roleIds) {
        userAdminService.create(username, password, realName, deptId, roleIds);
        return Result.ok("账号创建成功");
    }

    @PostMapping("/user/status")
    @RequiresPermission("user:manage")
    public Result<String> updateStatus(@RequestParam Long userId, @RequestParam Integer status) {
        userAdminService.updateStatus(userId, status);
        return Result.ok("状态已更新");
    }

    @PostMapping("/user/reset-password")
    @RequiresPermission("user:manage")
    public Result<String> resetPassword(@RequestParam Long userId, @RequestParam String newPassword) {
        userAdminService.resetPassword(userId, newPassword);
        return Result.ok("密码已重置，该用户下次登录需修改密码");
    }

    @PostMapping("/user/assign-roles")
    @RequiresPermission("role:assign")
    public Result<String> assignRoles(@RequestParam Long userId, @RequestParam List<Long> roleIds) {
        userAdminService.assignRoles(userId, roleIds);
        return Result.ok("角色已更新");
    }

    @GetMapping("/role/list")
    @RequiresPermission("user:manage")
    public Result<List<SysRole>> listRoles() {
        return Result.ok(userAdminService.listRoles());
    }

    @GetMapping("/dept/list")
    public Result<List<SysDept>> listDepts() {
        return Result.ok(userAdminService.listDepts());
    }

    @GetMapping("/audit/list")
    @RequiresPermission("audit:view")
    public Result<List<SysAuditLog>> listAudit(@RequestParam(defaultValue = "100") int limit) {
        return Result.ok(auditService.listRecent(limit));
    }
}
