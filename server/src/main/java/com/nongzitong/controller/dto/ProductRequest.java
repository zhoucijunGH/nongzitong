package com.nongzitong.controller.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 商品档案新增/修改请求。
 */
@Data
public class ProductRequest {

    @NotBlank(message = "请填写商品名称")
    private String name;

    /** 规格，如 50kg/袋 */
    @NotBlank(message = "请填写规格")
    private String spec;

    /** 单位，如 袋 */
    @NotBlank(message = "请填写单位")
    private String unit;

    /** 类别：化肥/农药/种子/其他 */
    @NotBlank(message = "请选择类别")
    private String category;

    /** 进价 */
    @DecimalMin(value = "0", message = "进价不能为负数")
    private BigDecimal purchasePrice;

    /** 售价 */
    @DecimalMin(value = "0", message = "售价不能为负数")
    private BigDecimal salePrice;

    /** 预警库存 */
    @DecimalMin(value = "0", message = "预警库存不能为负数")
    private BigDecimal warningStock;

    /** 期初库存（仅新增时生效） */
    @DecimalMin(value = "0", message = "期初库存不能为负数")
    private BigDecimal openingStock;

    /** 当前库存（仅新增时由期初库存推导） */
    @DecimalMin(value = "0", message = "库存不能为负数")
    private BigDecimal stock;

    /** 1 启用 0 停用 */
    private Integer status;

    private String remark;
}
