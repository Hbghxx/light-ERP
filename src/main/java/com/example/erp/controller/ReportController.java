package com.example.erp.controller;

import com.example.erp.common.RequiresPermission;
import com.example.erp.common.Result;
import com.example.erp.mapper.ReportMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 报表(阶段7): 仅聚合已通过单据(status=2),全局口径(报表查看权限本身即敏感权限)。
 */
@RestController
@RequestMapping("/report")
public class ReportController {

    @Autowired
    private ReportMapper reportMapper;

    /** 近 30 天出入库日报(按天+方向聚合) */
    @GetMapping("/daily")
    @RequiresPermission("report:view")
    public Result<List<Map<String, Object>>> daily() {
        return Result.ok(reportMapper.dailyFlows());
    }

    /** 库存价值榜(SKU 维度,单价×库存) */
    @GetMapping("/stock-value")
    @RequiresPermission("report:view")
    public Result<List<Map<String, Object>>> stockValue() {
        return Result.ok(reportMapper.stockValue());
    }
}
