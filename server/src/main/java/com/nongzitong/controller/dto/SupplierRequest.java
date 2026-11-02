package com.nongzitong.controller.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 供应商简档请求。
 */
@Data
public class SupplierRequest {

    @NotBlank(message = "请填写供应商名称")
    private String name;

    private String contact;

    private String phone;

    private String remark;
}
