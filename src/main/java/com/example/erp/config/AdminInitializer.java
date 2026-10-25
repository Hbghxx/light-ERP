package com.example.erp.config;

import com.example.erp.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 应用启动时保证系统存在管理员账号（sys_user 为空时初始化 admin，
 * 随机初始密码打印到应用日志，仅此一次）。
 */
@Component
public class AdminInitializer implements ApplicationRunner {

    @Autowired
    private AuthService authService;

    @Override
    public void run(ApplicationArguments args) {
        authService.initAdminIfAbsent();
    }
}
