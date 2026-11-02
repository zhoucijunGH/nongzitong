package com.nongzitong.controller.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 收款登记请求。
 */
@Data
public class PaymentRequest {

    /** 客户 id */
    @NotNull(message = "请选择客户")
    private Long customerId;

    /** 指定销售单（单据详情页收款时传；不传按 FIFO 分摊到该客户欠款单） */
    private Long saleId;

    /** 收款金额 */
    @NotNull(message = "请填写收款金额")
    @DecimalMin(value = "0", inclusive = false, message = "收款金额必须大于 0")
    private BigDecimal amount;

    /** 方式：现金/转账/其他 */
    @NotBlank(message = "请选择收款方式")
    private String method;

    /** 收款日期（默认今天） */
    private LocalDate payDate;

    private String remark;
}
