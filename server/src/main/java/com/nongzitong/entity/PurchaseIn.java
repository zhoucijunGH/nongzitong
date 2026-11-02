package com.nongzitong.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 采购入库单。
 */
@Data
@TableName("purchase_in")
public class PurchaseIn {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 单号 RK+yyyyMMdd+3位流水 */
    private String orderNo;

    /** 入库日期（日结维度） */
    private LocalDate orderDate;

    /** 供应商 id */
    private Long supplierId;

    /** 供应商名称（冗余快照） */
    private String supplierName;

    /** 总金额 = Σ(数量 × 进价) */
    private BigDecimal totalAmount;

    /** 备注 */
    private String remark;

    /** STORED 已入库 / VOID 已作废 */
    private String status;

    /** 作废原因 */
    private String voidReason;

    /** 作废人 */
    private String voidedByName;

    private LocalDateTime voidedAt;

    /** 制单人 id */
    private Long createdBy;

    /** 制单人姓名 */
    private String createdByName;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /** 明细（详情接口填充，不落库） */
    @TableField(exist = false)
    private List<PurchaseInItem> items;
}
