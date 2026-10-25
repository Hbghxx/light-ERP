package com.example.erp.mapper;

import com.example.erp.entity.SysRole;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface SysRoleMapper {

    @Select("SELECT id, role_code AS roleCode, role_name AS roleName, data_scope AS dataScope, is_builtin AS isBuiltin, created_at AS createdAt " +
            "FROM sys_role ORDER BY id")
    List<SysRole> listAll();

    @Select("SELECT id, role_code AS roleCode, role_name AS roleName, data_scope AS dataScope, is_builtin AS isBuiltin " +
            "FROM sys_role WHERE id = #{id}")
    SysRole findById(@Param("id") Long id);

    @Select("SELECT r.role_code FROM sys_role r JOIN sys_user_role ur ON ur.role_id = r.id WHERE ur.user_id = #{userId}")
    List<String> listRoleCodes(@Param("userId") Long userId);

    @Select("SELECT r.data_scope FROM sys_role r JOIN sys_user_role ur ON ur.role_id = r.id WHERE ur.user_id = #{userId}")
    List<String> listDataScopes(@Param("userId") Long userId);

    @Select("SELECT DISTINCT p.perm_code FROM sys_permission p " +
            "JOIN sys_role_permission rp ON rp.permission_id = p.id " +
            "JOIN sys_user_role ur ON ur.role_id = rp.role_id " +
            "WHERE ur.user_id = #{userId}")
    List<String> listPermissions(@Param("userId") Long userId);

    @Delete("DELETE FROM sys_user_role WHERE user_id = #{userId}")
    int deleteUserRoles(@Param("userId") Long userId);

    @Insert("INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (#{userId}, #{roleId})")
    int insertUserRole(@Param("userId") Long userId, @Param("roleId") Long roleId);
}
