package com.nongzitong.controller;

import com.nongzitong.common.PageResult;
import com.nongzitong.common.Result;
import com.nongzitong.controller.dto.PurchaseInRequest;
import com.nongzitong.controller.dto.VoidRequest;
import com.nongzitong.entity.PurchaseIn;
import com.nongzitong.security.LoginUser;
import com.nongzitong.service.PurchaseService;
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
 * 采购入库接口：店员与老板均可开单、作废（作废需原因，负库存需二次确认）。
 */
@RestController
@RequestMapping("/api/purchases")
public class PurchaseController {

    private final PurchaseService purchaseService;

    public PurchaseController(PurchaseService purchaseService) {
        this.purchaseService = purchaseService;
    }

    @PostMapping
    public Result<PurchaseIn> create(@Valid @RequestBody PurchaseInRequest req,
                                     @AuthenticationPrincipal LoginUser loginUser) {
        return Result.ok(purchaseService.create(req, loginUser));
    }

    @GetMapping
    public Result<PageResult<PurchaseIn>> page(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(purchaseService.page(dateFrom, dateTo, keyword, status, page, size));
    }

    @GetMapping("/{id}")
    public Result<PurchaseIn> get(@PathVariable Long id) {
        return Result.ok(purchaseService.get(id));
    }

    /** 作废：填原因；若回滚导致负库存，前端警示确认后带 confirm=true 重试 */
    @PostMapping("/{id}/void")
    public Result<Void> voidOrder(@PathVariable Long id, @Valid @RequestBody VoidRequest req,
                                  @AuthenticationPrincipal LoginUser loginUser) {
        purchaseService.voidOrder(id, req.getReason(), req.getConfirm(), loginUser);
        return Result.ok();
    }
}
