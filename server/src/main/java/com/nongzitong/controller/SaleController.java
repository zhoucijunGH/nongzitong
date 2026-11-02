package com.nongzitong.controller;

import com.nongzitong.common.PageResult;
import com.nongzitong.common.Result;
import com.nongzitong.controller.dto.SaleOrderRequest;
import com.nongzitong.controller.dto.VoidRequest;
import com.nongzitong.entity.SaleOrder;
import com.nongzitong.security.LoginUser;
import com.nongzitong.service.SaleService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 销售接口：开单/流水/详情（店员可用），作废需填原因。
 */
@RestController
@RequestMapping("/api/sales")
public class SaleController {

    private final SaleService saleService;

    public SaleController(SaleService saleService) {
        this.saleService = saleService;
    }

    @PostMapping
    public Result<SaleOrder> create(@Valid @RequestBody SaleOrderRequest req,
                                    @AuthenticationPrincipal LoginUser loginUser) {
        return Result.ok(saleService.create(req, loginUser));
    }

    @GetMapping
    public Result<PageResult<SaleOrder>> page(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String payMethod,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(saleService.page(dateFrom, dateTo, keyword, status, payMethod, page, size));
    }

    @GetMapping("/{id}")
    public Result<SaleOrder> get(@PathVariable Long id) {
        return Result.ok(saleService.get(id));
    }

    /** 作废：库存与欠款回滚，留痕原因/操作人/时间 */
    @PostMapping("/{id}/void")
    public Result<Void> voidOrder(@PathVariable Long id, @Valid @RequestBody VoidRequest req,
                                  @AuthenticationPrincipal LoginUser loginUser) {
        saleService.voidOrder(id, req.getReason(), loginUser);
        return Result.ok();
    }
}
