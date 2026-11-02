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
 * 销售单（核心表）。
 */
@Data
@TableName("sale_order")
public class SaleOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 单号 XS+yyyyMMdd+3位流水 */
    private String orderNo;

    /** 销售日期（日结维度） */
    private LocalDate orderDate;

    /** 客户 id（挂账必填，现结可空） */
    private Long customerId;

    /** 客户名称（冗余快照） */
    private String customerName;

    /** 收款方式：CASH 现结 / CREDIT 挂账 */
    private String payMethod;

    /** 合计金额 = Σ(数量 × 售价) */
    private BigDecimal totalAmount;

    /** 实收金额（现结=合计；挂账=已收款累计） */
    private BigDecimal receivedAmount;

    /** 备注 */
    private String remark;

    /** UNPAID 待收款 / PAID 已结清 / VOID 已作废 */
    private String status;

    /** 作废原因 */
    private String voidReason;

    /** 作废人 */
    private String voidedByName;

    private LocalDateTime voidedAt;

    /** 开单人 id */
    private Long createdBy;

    /** 开单人姓名 */
    private String createdByName;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /** 明细（详情接口填充，不落库） */
    @TableField(exist = false)
    private List<SaleOrderItem> items;
}
