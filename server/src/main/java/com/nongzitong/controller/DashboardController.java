package com.nongzitong.controller;

import com.nongzitong.common.Result;
import com.nongzitong.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 工作台接口：今日销售额/毛利/开单数、低库存、欠款总额、近 7 日趋势。
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    public Result<Map<String, Object>> dashboard() {
        return Result.ok(dashboardService.dashboard());
    }
}
