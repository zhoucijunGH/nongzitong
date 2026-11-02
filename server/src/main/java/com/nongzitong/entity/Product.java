package com.nongzitong.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品档案（化肥/农药/种子等农资商品）。
 */
@Data
@TableName("product")
public class Product {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 商品名称，如 尿素 */
    private String name;

    /** 规格，如 50kg/袋 */
    private String spec;

    /** 计量单位，如 袋/瓶/包 */
    private String unit;

    /** 类别：化肥/农药/种子/其他 */
    private String category;

    /** 进价（元） */
    private BigDecimal purchasePrice;

    /** 售价（元） */
    private BigDecimal salePrice;

    /** 预警库存（低于该值标红） */
    private BigDecimal warningStock;

    /** 期初库存 */
    private BigDecimal openingStock;

    /** 当前库存 = 期初 + 入库 - 销售出库 */
    private BigDecimal stock;

    /** 1 启用 0 停用 */
    private Integer status;

    /** 备注 */
    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
