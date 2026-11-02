package com.nongzitong.controller;

import com.nongzitong.common.Result;
import com.nongzitong.controller.dto.ProductRequest;
import com.nongzitong.entity.Product;
import com.nongzitong.security.LoginUser;
import com.nongzitong.service.AuthService;
import com.nongzitong.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
 * 商品档案接口：查询登录即可；维护仅老板（ADMIN）。
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;
    private final AuthService authService;

    public ProductController(ProductService productService, AuthService authService) {
        this.productService = productService;
        this.authService = authService;
    }

    @GetMapping
    public Result<List<Product>> list(@RequestParam(required = false) String keyword,
                                      @RequestParam(required = false) String category,
                                      @RequestParam(required = false) Integer status) {
        return Result.ok(productService.list(keyword, category, status));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Product> create(@Valid @RequestBody ProductRequest req,
                                  @AuthenticationPrincipal LoginUser loginUser) {
        return Result.ok(productService.create(req, authService.displayName(loginUser)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Product> update(@PathVariable Long id, @Valid @RequestBody ProductRequest req) {
        return Result.ok(productService.update(id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return Result.ok();
    }
}
