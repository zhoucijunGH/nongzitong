package com.nongzitong.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 库存流水（只增不改）。
 */
@Data
@TableName("stock_flow")
public class StockFlow {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 商品 id */
    private Long productId;

    /** 商品名称（冗余快照） */
    private String productName;

    /** 规格（冗余快照） */
    private String spec;

    /** 类型：IN 入库 / OUT 销售出库 / VOID 作废回滚 */
    private String flowType;

    /** 库存变化量（正增负减） */
    private BigDecimal delta;

    /** 变化后库存（快照） */
    private BigDecimal balance;

    /** 关联单号 */
    private String refNo;

    /** 操作人 */
    private String operator;

    private LocalDateTime createdAt;
}
