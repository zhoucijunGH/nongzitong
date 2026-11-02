package com.nongzitong.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nongzitong.entity.Product;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

public interface ProductMapper extends BaseMapper<Product> {

    /**
     * 扣减库存（数据库层负库存校验）：仅当库存足够时才扣减，
     * 返回 0 表示库存不足（并发下同样安全）。
     */
    @Update("UPDATE product SET stock = stock - #{qty} WHERE id = #{id} AND stock >= #{qty}")
    int deductStock(@Param("id") Long id, @Param("qty") BigDecimal qty);

    /** 增加库存（采购入库 / 作废回滚） */
    @Update("UPDATE product SET stock = stock + #{qty} WHERE id = #{id}")
    int addStock(@Param("id") Long id, @Param("qty") BigDecimal qty);

    /**
     * 无条件扣减库存：仅用于作废回滚（是否允许为负由业务层二次确认控制）。
     */
    @Update("UPDATE product SET stock = stock - #{qty} WHERE id = #{id}")
    int forceDeductStock(@Param("id") Long id, @Param("qty") BigDecimal qty);
}
