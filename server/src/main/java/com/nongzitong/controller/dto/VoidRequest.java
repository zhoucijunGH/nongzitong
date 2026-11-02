package com.nongzitong.controller.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 作废请求（采购单/销售单/收款记录）。
 */
@Data
public class VoidRequest {

    /** 作废原因 */
    @NotBlank(message = "请填写作废原因")
    private String reason;

    /** 确认负库存（作废导致库存为负时前端二次确认后传 true） */
    private Boolean confirm;
}
