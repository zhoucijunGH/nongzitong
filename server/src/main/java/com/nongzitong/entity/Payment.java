package com.nongzitong.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 收款记录（客户还款登记，只可作废不可修改）。
 */
@Data
@TableName("payment")
public class Payment {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 客户 id */
    private Long customerId;

    /** 客户名称（冗余快照） */
    private String customerName;

    /** 收款金额 */
    private BigDecimal amount;

    /** 方式：现金/转账/其他 */
    private String method;

    /** 经办人 */
    private String operator;

    /** 收款日期 */
    private LocalDate payDate;

    /** 备注 */
    private String remark;

    /** REGISTERED 已登记 / VOID 已作废 */
    private String status;

    /** 作废原因 */
    private String voidReason;

    /** 作废人 */
    private String voidedByName;

    private LocalDateTime voidedAt;

    /** 登记人 id */
    private Long createdBy;

    /** 登记人姓名 */
    private String createdByName;

    private LocalDateTime createdAt;
}
