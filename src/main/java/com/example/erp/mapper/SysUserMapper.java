package com.example.erp.mapper;

import com.example.erp.entity.SysUser;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface SysUserMapper {

    @Select("SELECT id, username, password, real_name AS realName, dept_id AS deptId, status, " +
            "error_count AS errorCount, need_change_pwd AS needChangePwd, last_login_at AS lastLoginAt, created_at AS createdAt " +
            "FROM sys_user WHERE username = #{username}")
    SysUser findByUsername(@Param("username") String username);

    @Select("SELECT id, username, password, real_name AS realName, dept_id AS deptId, status, " +
            "error_count AS errorCount, need_change_pwd AS needChangePwd, last_login_at AS lastLoginAt, created_at AS createdAt " +
            "FROM sys_user WHERE id = #{id}")
    SysUser findById(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM sys_user")
    long count();

    @Insert("INSERT INTO sys_user (username, password, real_name, dept_id, status, error_count, need_change_pwd) " +
            "VALUES (#{username}, #{password}, #{realName}, #{deptId}, #{status}, 0, #{needChangePwd})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SysUser user);

    @Update("UPDATE sys_user SET password = #{password}, need_change_pwd = #{needChangePwd}, error_count = 0 WHERE id = #{id}")
    int updatePassword(@Param("id") Long id, @Param("password") String password, @Param("needChangePwd") int needChangePwd);

    /** 解锁时同时清零错误计数 */
    @Update("UPDATE sys_user SET status = #{status}, error_count = CASE WHEN #{status} = 1 THEN 0 ELSE error_count END WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    @Update("UPDATE sys_user SET error_count = error_count + 1 WHERE id = #{id}")
    int incrErrorCount(@Param("id") Long id);

    @Update("UPDATE sys_user SET status = 2 WHERE id = #{id} AND error_count >= 5")
    int lockIfExceeded(@Param("id") Long id);

    @Update("UPDATE sys_user SET error_count = 0, last_login_at = NOW() WHERE id = #{id}")
    int recordLoginSuccess(@Param("id") Long id);

    @Select("SELECT u.id, u.username, u.real_name AS realName, u.status, u.error_count AS errorCount, " +
            "u.need_change_pwd AS needChangePwd, u.last_login_at AS lastLoginAt, " +
            "d.name AS deptName, " +
            "(SELECT GROUP_CONCAT(r.role_name SEPARATOR '、') FROM sys_user_role ur JOIN sys_role r ON r.id = ur.role_id WHERE ur.user_id = u.id) AS roleNames " +
            "FROM sys_user u LEFT JOIN sys_dept d ON d.id = u.dept_id ORDER BY u.id")
    List<SysUser> listAll();

    @Select("SELECT role_id FROM sys_user_role WHERE user_id = #{userId}")
    List<Long> listRoleIds(@Param("userId") Long userId);
}
