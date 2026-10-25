package com.example.erp.service;

import com.example.erp.common.BusinessException;
import com.example.erp.common.JwtUtil;
import com.example.erp.common.UnauthorizedException;
import com.example.erp.common.UserContext;
import com.example.erp.entity.SysDept;
import com.example.erp.entity.SysUser;
import com.example.erp.mapper.SysDeptMapper;
import com.example.erp.mapper.SysRoleMapper;
import com.example.erp.mapper.SysUserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录认证与用户上下文构建。
 * 上下文做 60 秒本地缓存；账号停用/锁定状态每次请求直查主键表即时生效。
 */
@Service
public class AuthService {

    private static final long CACHE_TTL_MILLIS = 60_000L;

    @Autowired private SysUserMapper userMapper;
    @Autowired private SysRoleMapper roleMapper;
    @Autowired private SysDeptMapper deptMapper;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private AuditService auditService;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final SecureRandom random = new SecureRandom();
    private final Map<Long, CacheEntry> contextCache = new ConcurrentHashMap<>();

    /** 供用户不存在时的等时比对使用（启动时生成，格式保证合法） */
    private static final String DUMMY_HASH = new BCryptPasswordEncoder().encode("timing-equalizer");

    private record CacheEntry(UserContext ctx, long expireAt) {}

    public Map<String, Object> login(String username, String rawPassword, String ip) {
        if (username == null || username.isBlank() || rawPassword == null || rawPassword.isEmpty()) {
            throw new BusinessException("用户名和密码不能为空");
        }
        SysUser user = userMapper.findByUsername(username.trim());
        // 用户不存在也走一次 BCrypt 比对，避免响应时间差异泄露账号是否存在
        if (user == null) {
            encoder.matches(rawPassword, DUMMY_HASH);
            throw new BusinessException("用户名或密码错误");
        }
        if (user.getStatus() == 0) {
            throw new BusinessException("账号已停用，请联系管理员");
        }
        if (user.getStatus() == 2) {
            throw new BusinessException("账号已锁定，请联系管理员解锁");
        }
        if (!encoder.matches(rawPassword, user.getPassword())) {
            userMapper.incrErrorCount(user.getId());
            userMapper.lockIfExceeded(user.getId());
            throw new BusinessException(userMapper.findById(user.getId()).getStatus() == 2
                    ? "密码连续错误 5 次，账号已锁定，请联系管理员"
                    : "用户名或密码错误");
        }
        userMapper.recordLoginSuccess(user.getId());
        auditService.write(user.getId(), "LOGIN", null, null, null,
                Map.of("username", user.getUsername()), ip);
        return Map.of(
                "token", jwtUtil.create(user.getId(), user.getUsername()),
                "username", user.getUsername(),
                "realName", user.getRealName() == null ? user.getUsername() : user.getRealName(),
                "needChangePwd", user.getNeedChangePwd() != null && user.getNeedChangePwd() == 1);
    }

    /** 构建用户上下文（带 TTL 缓存）；停用/锁定每次请求直查主键表，即时生效 */
    public UserContext loadContext(Long userId) {
        SysUser gate = userMapper.findById(userId);
        if (gate == null || (gate.getStatus() != null && gate.getStatus() != 1)) {
            contextCache.remove(userId);
            throw new UnauthorizedException("账号不可用，请重新登录");
        }
        long now = System.currentTimeMillis();
        CacheEntry entry = contextCache.get(userId);
        if (entry != null && entry.expireAt() > now) {
            return entry.ctx();
        }
        UserContext ctx = buildContext(userId);
        contextCache.put(userId, new CacheEntry(ctx, now + CACHE_TTL_MILLIS));
        return ctx;
    }

    public void invalidate(Long userId) {
        contextCache.remove(userId);
    }

    /** 校验 token 并返回 userId，无效返回 null（拦截器入口） */
    public Long parseUserId(String token) {
        return jwtUtil.parseUserId(token);
    }

    private UserContext buildContext(Long userId) {
        SysUser user = userMapper.findById(userId);
        if (user == null || user.getStatus() == 0 || user.getStatus() == 2) {
            throw new UnauthorizedException("账号不可用，请重新登录");
        }
        UserContext ctx = new UserContext();
        ctx.setUserId(user.getId());
        ctx.setUsername(user.getUsername());
        ctx.setRealName(user.getRealName() == null ? user.getUsername() : user.getRealName());
        ctx.setDeptId(user.getDeptId());

        List<String> roleCodes = roleMapper.listRoleCodes(userId);
        ctx.setAdmin(roleCodes.contains("ADMIN"));

        List<String> scopes = roleMapper.listDataScopes(userId);
        ctx.setDataScope(maxScope(scopes));

        if (ctx.isAdmin()) {
            ctx.setPermissions(Set.of()); // ADMIN 短路放行，无需加载权限点
        } else {
            ctx.setPermissions(new HashSet<>(roleMapper.listPermissions(userId)));
        }

        SysDept dept = deptMapper.findById(user.getDeptId());
        ctx.setDeptPath(dept == null ? "/" : dept.getPath());
        return ctx;
    }

    /** 多角色取最大数据范围：ALL > DEPT_AND_CHILD > SELF */
    private String maxScope(List<String> scopes) {
        if (scopes.contains("ALL")) {
            return "ALL";
        }
        if (scopes.contains("DEPT_AND_CHILD")) {
            return "DEPT_AND_CHILD";
        }
        return "SELF";
    }

    public void changePassword(Long userId, String oldPassword, String newPassword) {
        SysUser user = userMapper.findById(userId);
        if (user == null || !encoder.matches(oldPassword, user.getPassword())) {
            throw new BusinessException("原密码错误");
        }
        updatePassword(userId, newPassword);
    }

    /** 密码策略（简版）：长度至少 8 位；统一走 BCrypt 并清除上下文缓存 */
    public void updatePassword(Long userId, String newPassword) {
        if (newPassword == null || newPassword.length() < 8) {
            throw new BusinessException("新密码长度至少 8 位");
        }
        userMapper.updatePassword(userId, encoder.encode(newPassword), 0);
        invalidate(userId);
    }

    public String encryptPassword(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    /** 管理员重置：重置后强制本人下次登录改密（need_change_pwd=1） */
    public void adminResetPassword(Long userId, String newPassword) {
        if (newPassword == null || newPassword.length() < 8) {
            throw new BusinessException("新密码长度至少 8 位");
        }
        userMapper.updatePassword(userId, encoder.encode(newPassword), 1);
        invalidate(userId);
    }

    /** sys_user 为空时初始化 admin：随机密码打印到日志（仅一次），并写入审计 */
    public void initAdminIfAbsent() {
        if (userMapper.count() > 0) {
            return;
        }
        String pwd = randomPassword();
        SysUser admin = new SysUser();
        admin.setUsername("admin");
        admin.setPassword(encoder.encode(pwd));
        admin.setRealName("系统管理员");
        admin.setDeptId(1L);
        admin.setStatus(1);
        admin.setNeedChangePwd(1);
        userMapper.insert(admin);
        roleMapper.insertUserRole(admin.getId(), 1L); // id=1 ADMIN
        auditService.write(admin.getId(), "ADMIN_INIT", "USER", admin.getId(), null,
                Map.of("username", "admin"), null);
        org.slf4j.LoggerFactory.getLogger(AuthService.class)
                .warn("已初始化管理员账号 admin，初始密码: {}（请立即登录修改）", pwd);
    }

    private String randomPassword() {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return sb.toString();
    }
}
