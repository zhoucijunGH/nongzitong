package com.nongzitong.controller.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 采购入库单请求。
 */
@Data
public class PurchaseInRequest {

    /** 入库日期（默认今天） */
    private LocalDate orderDate;

    /** 供应商 id */
    @NotNull(message = "请选择供应商")
    private Long supplierId;

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

        @NotNull(message = "请填写进价")
        @DecimalMin(value = "0", message = "进价不能为负数")
        private BigDecimal price;
    }
}
