package com.nongzitong.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客户档案（赊销挂账主体）。
 */
@Data
@TableName("customer")
public class Customer {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 姓名/名称 */
    private String name;

    /** 电话 */
    private String phone;

    /** 地址 */
    private String address;

    /** 备注 */
    private String remark;

    /** 1 启用 0 停用（停用后不可被新单引用） */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
