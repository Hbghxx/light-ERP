package com.example.erp.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口权限声明：权限码 = 资源:动作（见 sys_permission 种子数据）。
 * 拦截器校验 UserContext；ADMIN 角色短路放行。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPermission {
    String value();
}
