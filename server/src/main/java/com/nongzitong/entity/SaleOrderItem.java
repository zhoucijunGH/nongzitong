package com.nongzitong.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 销售单明细（含单据进价快照，毛利 = 售价 - 进价快照）。
 */
@Data
@TableName("sale_order_item")
public class SaleOrderItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 销售单 id */
    private Long saleId;

    /** 商品 id */
    private Long productId;

    /** 商品名称（冗余快照） */
    private String productName;

    /** 规格（冗余快照） */
    private String spec;

    /** 数量 */
    private BigDecimal qty;

    /** 售价（改价后以本单据为准） */
    private BigDecimal price;

    /** 小计 = 数量 × 售价 */
    private BigDecimal amount;

    /** 单据进价快照（下单时商品档案进价，用于毛利） */
    private BigDecimal costPrice;

    /** 原售价（改价留痕：改价前的档案售价） */
    private BigDecimal originPrice;

    /** 改价操作人（未改价为空） */
    private String priceOperator;
}
