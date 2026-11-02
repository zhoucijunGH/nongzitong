package com.nongzitong.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 收款分摊：一笔收款摊到具体销售单（作废时按此回滚）。
 */
@Data
@TableName("payment_alloc")
public class PaymentAlloc {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 收款记录 id */
    private Long paymentId;

    /** 销售单 id */
    private Long saleId;

    /** 分摊金额 */
    private BigDecimal amount;
}
