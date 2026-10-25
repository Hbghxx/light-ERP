package com.example.erp.entity;

import lombok.Data;

@Data
public class SysDept {
    private Long id;
    private String name;
    private Long parentId;
    private String path;   // 物化路径，如 /1/3/7/
    private Integer level;
}
