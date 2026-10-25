package com.example.erp.common;

import java.util.Collections;
import java.util.Set;

/**
 * 当前登录用户上下文（ThreadLocal，拦截器在请求结束后清理）。
 * 数据权限三档：ALL / DEPT_AND_CHILD / SELF，多角色取最大范围；ADMIN 拥有全部权限点。
 */
public class UserContext {

    private static final ThreadLocal<UserContext> HOLDER = new ThreadLocal<>();

    private Long userId;
    private String username;
    private String realName;
    private Long deptId;
    private String deptPath;
    private String dataScope = "SELF";
    private boolean admin;
    private Set<String> permissions = Collections.emptySet();

    public static void set(UserContext ctx) {
        HOLDER.set(ctx);
    }

    public static UserContext get() {
        UserContext ctx = HOLDER.get();
        if (ctx == null) {
            throw new UnauthorizedException("未登录或登录已过期");
        }
        return ctx;
    }

    public static Long currentUserId() {
        return get().getUserId();
    }

    public static void clear() {
        HOLDER.remove();
    }

    public boolean hasPermission(String permCode) {
        return admin || permissions.contains(permCode);
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getRealName() { return realName; }
    public void setRealName(String realName) { this.realName = realName; }
    public Long getDeptId() { return deptId; }
    public void setDeptId(Long deptId) { this.deptId = deptId; }
    public String getDeptPath() { return deptPath; }
    public void setDeptPath(String deptPath) { this.deptPath = deptPath; }
    public String getDataScope() { return dataScope; }
    public void setDataScope(String dataScope) { this.dataScope = dataScope; }
    public boolean isAdmin() { return admin; }
    public void setAdmin(boolean admin) { this.admin = admin; }
    public Set<String> getPermissions() { return permissions; }
    public void setPermissions(Set<String> permissions) { this.permissions = permissions; }
}
