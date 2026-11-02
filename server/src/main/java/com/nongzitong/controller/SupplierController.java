package com.nongzitong.controller;

import com.nongzitong.common.Result;
import com.nongzitong.controller.dto.SupplierRequest;
import com.nongzitong.entity.Supplier;
import com.nongzitong.service.SupplierService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 供应商接口：查询登录即可；维护仅老板（ADMIN）。
 */
@RestController
@RequestMapping("/api/suppliers")
public class SupplierController {

    private final SupplierService supplierService;

    public SupplierController(SupplierService supplierService) {
        this.supplierService = supplierService;
    }

    @GetMapping
    public Result<List<Supplier>> list(@RequestParam(required = false) String keyword) {
        return Result.ok(supplierService.list(keyword));
    }

    @GetMapping("/{id}")
    public Result<Supplier> get(@PathVariable Long id) {
        return Result.ok(supplierService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Supplier> create(@Valid @RequestBody SupplierRequest req) {
        return Result.ok(supplierService.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Supplier> update(@PathVariable Long id, @Valid @RequestBody SupplierRequest req) {
        return Result.ok(supplierService.update(id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> delete(@PathVariable Long id) {
        supplierService.delete(id);
        return Result.ok();
    }
}
