package com.nongzitong.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nongzitong.entity.Customer;
import com.nongzitong.entity.Payment;
import com.nongzitong.entity.SaleOrder;
import com.nongzitong.mapper.CustomerMapper;
import com.nongzitong.mapper.PaymentMapper;
import com.nongzitong.mapper.SaleOrderMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 欠款服务：客户欠款 = 该客户全部“待收款”销售单未收金额之和。
 */
@Service
public class DebtService {

    private final CustomerMapper customerMapper;
    private final SaleOrderMapper orderMapper;
    private final PaymentMapper paymentMapper;

    public DebtService(CustomerMapper customerMapper, SaleOrderMapper orderMapper,
                       PaymentMapper paymentMapper) {
        this.customerMapper = customerMapper;
        this.orderMapper = orderMapper;
        this.paymentMapper = paymentMapper;
    }

    /**
     * 客户欠款余额列表，按余额降序；含未结单数、最近欠款日期、累计收款。
     */
    public List<Map<String, Object>> list(String keyword) {
        List<SaleOrder> unpaid = orderMapper.selectList(new LambdaQueryWrapper<SaleOrder>()
                .eq(SaleOrder::getStatus, "UNPAID")
                .and(StringUtils.hasText(keyword), w -> w
                        .like(SaleOrder::getCustomerName, keyword)
                        .or().like(SaleOrder::getOrderNo, keyword)));

        Map<Long, Map<String, Object>> groups = new LinkedHashMap<>();
        for (SaleOrder o : unpaid) {
            if (o.getCustomerId() == null) {
                continue;
            }
            Map<String, Object> g = groups.computeIfAbsent(o.getCustomerId(), k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("customerId", o.getCustomerId());
                m.put("customerName", o.getCustomerName());
                m.put("orderCount", 0L);
                m.put("debtAmount", BigDecimal.ZERO);
                m.put("lastDate", o.getOrderDate() == null ? null : o.getOrderDate().toString());
                return m;
            });
            g.put("orderCount", (Long) g.get("orderCount") + 1);
            BigDecimal remain = nvl(o.getTotalAmount()).subtract(nvl(o.getReceivedAmount()));
            g.put("debtAmount", ((BigDecimal) g.get("debtAmount")).add(remain));
            String last = o.getOrderDate() == null ? null : o.getOrderDate().toString();
            String cur = (String) g.get("lastDate");
            if (last != null && (cur == null || last.compareTo(cur) > 0)) {
                g.put("lastDate", last);
            }
        }

        // 补充客户档案信息（电话/地址）与累计已收款
        List<Customer> customers = customerMapper.selectList(new LambdaQueryWrapper<Customer>()
                .and(StringUtils.hasText(keyword), w -> w
                        .like(Customer::getName, keyword)
                        .or().like(Customer::getPhone, keyword)));
        Map<Long, Customer> customerMap = new LinkedHashMap<>();
        customers.forEach(c -> customerMap.put(c.getId(), c));
        if (StringUtils.hasText(keyword)) {
            groups.keySet().removeIf(id -> !customerMap.containsKey(id));
        }

        List<Map<String, Object>> result = new ArrayList<>(groups.values());
        for (Map<String, Object> g : result) {
            Long customerId = (Long) g.get("customerId");
            Customer c = customerMap.get(customerId);
            g.put("phone", c == null ? null : c.getPhone());
            g.put("address", c == null ? null : c.getAddress());
            BigDecimal paid = paymentMapper.selectList(new LambdaQueryWrapper<Payment>()
                            .eq(Payment::getCustomerId, customerId)
                            .eq(Payment::getStatus, "REGISTERED"))
                    .stream().map(Payment::getAmount).map(this::nvl)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);
            g.put("paidAmount", paid);
            g.put("debtAmount", ((BigDecimal) g.get("debtAmount")).setScale(2, RoundingMode.HALF_UP));
        }
        // 按余额降序
        result.sort(Comparator.comparing((Map<String, Object> m) -> (BigDecimal) m.get("debtAmount"),
                Comparator.reverseOrder()));
        return result;
    }

    /** 欠款构成明细：该客户全部待收款销售单 + 未收金额 */
    public List<Map<String, Object>> orders(Long customerId) {
        List<SaleOrder> list = orderMapper.selectList(new LambdaQueryWrapper<SaleOrder>()
                .eq(SaleOrder::getCustomerId, customerId)
                .eq(SaleOrder::getStatus, "UNPAID")
                .orderByAsc(SaleOrder::getId));
        List<Map<String, Object>> result = new ArrayList<>();
        for (SaleOrder o : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", o.getId());
            m.put("orderNo", o.getOrderNo());
            m.put("orderDate", o.getOrderDate() == null ? null : o.getOrderDate().toString());
            m.put("totalAmount", nvl(o.getTotalAmount()).setScale(2, RoundingMode.HALF_UP));
            m.put("receivedAmount", nvl(o.getReceivedAmount()).setScale(2, RoundingMode.HALF_UP));
            m.put("remainAmount", nvl(o.getTotalAmount()).subtract(nvl(o.getReceivedAmount()))
                    .setScale(2, RoundingMode.HALF_UP));
            m.put("createdByName", o.getCreatedByName());
            result.add(m);
        }
        return result;
    }

    /** 客户当前欠款余额 */
    public BigDecimal balanceOf(Long customerId) {
        List<SaleOrder> unpaid = orderMapper.selectList(new LambdaQueryWrapper<SaleOrder>()
                .eq(SaleOrder::getCustomerId, customerId)
                .eq(SaleOrder::getStatus, "UNPAID"));
        BigDecimal sum = BigDecimal.ZERO;
        for (SaleOrder o : unpaid) {
            sum = sum.add(nvl(o.getTotalAmount()).subtract(nvl(o.getReceivedAmount())));
        }
        return sum.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
