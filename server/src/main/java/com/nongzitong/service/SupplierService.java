package com.nongzitong.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nongzitong.common.BizException;
import com.nongzitong.controller.dto.SupplierRequest;
import com.nongzitong.entity.Supplier;
import com.nongzitong.mapper.SupplierMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 供应商简档服务。
 */
@Service
public class SupplierService {

    private final SupplierMapper supplierMapper;

    public SupplierService(SupplierMapper supplierMapper) {
        this.supplierMapper = supplierMapper;
    }

    public List<Supplier> list(String keyword) {
        return supplierMapper.selectList(new LambdaQueryWrapper<Supplier>()
                .and(StringUtils.hasText(keyword), w -> w
                        .like(Supplier::getName, keyword)
                        .or().like(Supplier::getContact, keyword)
                        .or().like(Supplier::getPhone, keyword))
                .orderByDesc(Supplier::getId));
    }

    public Supplier get(Long id) {
        Supplier supplier = supplierMapper.selectById(id);
        if (supplier == null) {
            throw new BizException(404, "供应商不存在");
        }
        return supplier;
    }

    public Supplier create(SupplierRequest req) {
        Supplier supplier = new Supplier();
        apply(supplier, req);
        supplierMapper.insert(supplier);
        return supplier;
    }

    public Supplier update(Long id, SupplierRequest req) {
        Supplier supplier = get(id);
        apply(supplier, req);
        supplierMapper.updateById(supplier);
        return supplier;
    }

    public void delete(Long id) {
        get(id);
        supplierMapper.deleteById(id);
    }

    private void apply(Supplier supplier, SupplierRequest req) {
        supplier.setName(req.getName().trim());
        supplier.setContact(req.getContact());
        supplier.setPhone(req.getPhone());
        supplier.setRemark(req.getRemark());
    }
}
