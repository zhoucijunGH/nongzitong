package com.nongzitong.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nongzitong.common.BizException;
import com.nongzitong.controller.dto.CustomerRequest;
import com.nongzitong.entity.Customer;
import com.nongzitong.mapper.CustomerMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 客户档案服务（赊销挂账主体）。
 */
@Service
public class CustomerService {

    private final CustomerMapper customerMapper;

    public CustomerService(CustomerMapper customerMapper) {
        this.customerMapper = customerMapper;
    }

    /** 客户列表；status 为空返回全部（开单选客户时含停用标记） */
    public List<Customer> list(String keyword, Integer status) {
        return customerMapper.selectList(new LambdaQueryWrapper<Customer>()
                .eq(status != null, Customer::getStatus, status)
                .and(StringUtils.hasText(keyword), w -> w
                        .like(Customer::getName, keyword)
                        .or().like(Customer::getPhone, keyword))
                .orderByDesc(Customer::getId));
    }

    public Customer get(Long id) {
        Customer customer = customerMapper.selectById(id);
        if (customer == null) {
            throw new BizException(404, "客户不存在");
        }
        return customer;
    }

    public Customer create(CustomerRequest req) {
        Customer customer = new Customer();
        apply(customer, req);
        customer.setStatus(req.getStatus() == null ? 1 : req.getStatus());
        customerMapper.insert(customer);
        return customer;
    }

    public Customer update(Long id, CustomerRequest req) {
        Customer customer = get(id);
        apply(customer, req);
        if (req.getStatus() != null) {
            customer.setStatus(req.getStatus());
        }
        customerMapper.updateById(customer);
        return customer;
    }

    public void delete(Long id) {
        get(id);
        customerMapper.deleteById(id);
    }

    private void apply(Customer customer, CustomerRequest req) {
        customer.setName(req.getName().trim());
        customer.setPhone(req.getPhone());
        customer.setAddress(req.getAddress());
        customer.setRemark(req.getRemark());
    }
}
