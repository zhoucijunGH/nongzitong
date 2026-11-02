package com.nongzitong.controller;

import com.nongzitong.common.Result;
import com.nongzitong.controller.dto.CustomerRequest;
import com.nongzitong.entity.Customer;
import com.nongzitong.service.CustomerService;
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
 * 客户档案接口：查询登录即可（开单选客户）；维护仅老板（ADMIN）。
 */
@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping
    public Result<List<Customer>> list(@RequestParam(required = false) String keyword,
                                       @RequestParam(required = false) Integer status) {
        return Result.ok(customerService.list(keyword, status));
    }

    @GetMapping("/{id}")
    public Result<Customer> get(@PathVariable Long id) {
        return Result.ok(customerService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Customer> create(@Valid @RequestBody CustomerRequest req) {
        return Result.ok(customerService.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Customer> update(@PathVariable Long id, @Valid @RequestBody CustomerRequest req) {
        return Result.ok(customerService.update(id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> delete(@PathVariable Long id) {
        customerService.delete(id);
        return Result.ok();
    }
}
