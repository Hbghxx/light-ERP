package com.example.erp.config;

import com.example.erp.common.ForbiddenException;
import com.example.erp.common.RequiresPermission;
import com.example.erp.common.UnauthorizedException;
import com.example.erp.common.UserContext;
import com.example.erp.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 认证 + 操作权限拦截：
 * 1. 解析 Bearer JWT -> UserContext（ThreadLocal，请求结束清理）；
 * 2. 账号停用/锁定状态每次请求直查主键表，即使 token 未过期也立即生效；
 * 3. 方法上的 @RequiresPermission 逐码校验，ADMIN 短路。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Autowired
    private AuthService authService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        // 防御 ThreadLocal 残留：preHandle 抛异常时 Spring 不会回调本拦截器的 afterCompletion
        UserContext.clear();
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            throw new UnauthorizedException("未登录，请先登录");
        }
        Long userId = authService.parseUserId(auth.substring(7));
        if (userId == null) {
            throw new UnauthorizedException("登录已过期，请重新登录");
        }
        UserContext ctx = authService.loadContext(userId);
        UserContext.set(ctx);

        RequiresPermission required = handlerMethod.getMethodAnnotation(RequiresPermission.class);
        if (required != null && !ctx.hasPermission(required.value())) {
            throw new ForbiddenException("权限不足，缺少: " + required.value());
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }
}
