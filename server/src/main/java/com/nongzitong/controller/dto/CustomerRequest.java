package com.nongzitong.controller.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 客户档案请求。
 */
@Data
public class CustomerRequest {

    @NotBlank(message = "请填写客户名称")
    private String name;

    private String phone;

    private String address;

    private String remark;

    /** 1 启用 0 停用 */
    private Integer status;
}
