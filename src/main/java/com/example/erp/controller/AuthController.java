package com.example.erp.controller;

import com.example.erp.common.Result;
import com.example.erp.common.UserContext;
import com.example.erp.entity.SysAuditLog;
import com.example.erp.entity.SysUser;
import com.example.erp.mapper.SysRoleMapper;
import com.example.erp.mapper.SysUserMapper;
import com.example.erp.service.AuditService;
import com.example.erp.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 登录 / 当前用户 / 自助改密。登录接口在拦截器白名单中，其余全部需要 token。
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired private AuthService authService;
    @Autowired private SysUserMapper userMapper;
    @Autowired private SysRoleMapper roleMapper;
    @Autowired private AuditService auditService;

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestParam String username,
                                             @RequestParam String password) {
        // ip 传 null，由 AuditService 统一解析（优先 X-Forwarded-For，兼容 Nginx 反代）
        return Result.ok(authService.login(username, password, null));
    }

    @GetMapping("/me")
    public Result<Map<String, Object>> me() {
        UserContext ctx = UserContext.get();
        SysUser user = userMapper.findById(ctx.getUserId());
        return Result.ok(Map.of(
                "userId", ctx.getUserId(),
                "username", ctx.getUsername(),
                "realName", ctx.getRealName(),
                "admin", ctx.isAdmin(),
                "permissions", ctx.getPermissions(),
                "dataScope", ctx.getDataScope(),
                "needChangePwd", user != null && user.getNeedChangePwd() != null && user.getNeedChangePwd() == 1));
    }

    @PostMapping("/change-password")
    public Result<String> changePassword(@RequestParam String oldPassword,
                                         @RequestParam String newPassword) {
        authService.changePassword(UserContext.currentUserId(), oldPassword, newPassword);
        return Result.ok("密码修改成功，请重新登录");
    }

    /** 我的操作日志（无需专门权限，本人可见） */
    @GetMapping("/my-audit")
    public Result<List<SysAuditLog>> myAudit() {
        return Result.ok(auditService.listRecent(50).stream()
                .filter(a -> UserContext.currentUserId().equals(a.getUserId()))
                .limit(20)
                .toList());
    }

    @GetMapping("/roles")
    public Result<?> myRoles() {
        return Result.ok(roleMapper.listRoleCodes(UserContext.currentUserId()));
    }

    @GetMapping("/user-check")
    public Result<?> userCheck() {
        // 供拦截器外的前端做 token 健康检查；返回账号是否存在且可用
        SysUser user = userMapper.findById(UserContext.currentUserId());
        return Result.ok(user != null && user.getStatus() == 1);
    }
}
