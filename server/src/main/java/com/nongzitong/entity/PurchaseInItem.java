package com.nongzitong.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 采购入库单明细。
 */
@Data
@TableName("purchase_in_item")
public class PurchaseInItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 采购单 id */
    private Long inId;

    /** 商品 id */
    private Long productId;

    /** 商品名称（冗余快照） */
    private String productName;

    /** 规格（冗余快照） */
    private String spec;

    /** 数量 */
    private BigDecimal qty;

    /** 进价（以单据行为准，可与档案价不同） */
    private BigDecimal price;

    /** 金额 = 数量 × 进价 */
    private BigDecimal amount;
}
