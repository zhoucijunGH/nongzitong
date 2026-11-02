package com.nongzitong.controller;

import com.nongzitong.common.Result;
import com.nongzitong.service.DebtService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 欠款接口：客户余额列表 + 欠款构成明细（店员可见欠款查询，收款登记另走 /api/payments）。
 */
@RestController
@RequestMapping("/api/debts")
public class DebtController {

    private final DebtService debtService;

    public DebtController(DebtService debtService) {
        this.debtService = debtService;
    }

    @GetMapping
    public Result<List<Map<String, Object>>> list(@RequestParam(required = false) String keyword) {
        return Result.ok(debtService.list(keyword));
    }

    /** 欠款构成：该客户全部待收款销售单 */
    @GetMapping("/{customerId}/orders")
    public Result<List<Map<String, Object>>> orders(@PathVariable Long customerId) {
        return Result.ok(debtService.orders(customerId));
    }

    /** 客户欠款余额（小程序工作台/收款页用） */
    @GetMapping("/{customerId}/balance")
    public Result<BigDecimal> balance(@PathVariable Long customerId) {
        return Result.ok(debtService.balanceOf(customerId));
    }
}
