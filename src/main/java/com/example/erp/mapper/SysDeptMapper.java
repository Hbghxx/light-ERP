package com.example.erp.mapper;

import com.example.erp.entity.SysDept;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysDeptMapper {

    @Select("SELECT id, name, parent_id AS parentId, path, level FROM sys_dept ORDER BY level, id")
    List<SysDept> listAll();

    @Select("SELECT id, name, parent_id AS parentId, path, level FROM sys_dept WHERE id = #{id}")
    SysDept findById(@Param("id") Long id);

    /** 子树命中数（用于校验部门存在性与物化路径查询演示） */
    @Select("SELECT COUNT(*) FROM sys_dept WHERE path LIKE CONCAT(#{path}, '%')")
    int countSubtree(@Param("path") String path);
}
