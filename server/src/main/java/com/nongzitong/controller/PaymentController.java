package com.nongzitong.controller;

import com.nongzitong.common.PageResult;
import com.nongzitong.common.Result;
import com.nongzitong.controller.dto.PaymentRequest;
import com.nongzitong.controller.dto.VoidRequest;
import com.nongzitong.entity.Payment;
import com.nongzitong.security.LoginUser;
import com.nongzitong.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
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
 * 收款记录接口：登记与作废仅老板（ADMIN），店员无收款权限。
 */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Payment> create(@Valid @RequestBody PaymentRequest req,
                                  @AuthenticationPrincipal LoginUser loginUser) {
        return Result.ok(paymentService.create(req, loginUser));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<PageResult<Payment>> page(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(paymentService.page(dateFrom, dateTo, keyword, status, page, size));
    }

    /** 收款作废：不可修改，只能作废重录，作废后余额回滚 */
    @PostMapping("/{id}/void")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> voidPayment(@PathVariable Long id, @Valid @RequestBody VoidRequest req,
                                    @AuthenticationPrincipal LoginUser loginUser) {
        paymentService.voidPayment(id, req.getReason(), loginUser);
        return Result.ok();
    }
}
