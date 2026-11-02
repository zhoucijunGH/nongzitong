package com.nongzitong.controller.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 销售开单请求。
 */
@Data
public class SaleOrderRequest {

    /** 销售日期（默认今天） */
    private LocalDate orderDate;

    /** 收款方式：CASH 现结 / CREDIT 挂账 */
    @NotBlank(message = "请选择收款方式")
    private String payMethod;

    /** 客户 id（挂账必填） */
    private Long customerId;

    /** 备注 */
    private String remark;

    @NotEmpty(message = "请添加商品明细")
    @Valid
    private List<Item> items;

    @Data
    public static class Item {
        @NotNull(message = "请选择商品")
        private Long productId;

        @NotNull(message = "请填写数量")
        @DecimalMin(value = "0", inclusive = false, message = "数量必须大于 0")
        private BigDecimal qty;

        /** 售价（可改价，与档案价不同时留痕） */
        @NotNull(message = "请填写售价")
        @DecimalMin(value = "0", message = "售价不能为负数")
        private BigDecimal price;
    }
}
