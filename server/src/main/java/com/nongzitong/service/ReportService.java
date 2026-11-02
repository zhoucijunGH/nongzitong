package com.nongzitong.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nongzitong.entity.Payment;
import com.nongzitong.entity.PurchaseIn;
import com.nongzitong.entity.SaleOrder;
import com.nongzitong.entity.SaleOrderItem;
import com.nongzitong.mapper.PaymentMapper;
import com.nongzitong.mapper.PurchaseInMapper;
import com.nongzitong.mapper.SaleOrderItemMapper;
import com.nongzitong.mapper.SaleOrderMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 经营报表服务：区间汇总销售额/成本/毛利/现结挂账拆分/收款/新增欠款 + 商品销量 Top。
 */
@Service
public class ReportService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final SaleOrderMapper orderMapper;
    private final SaleOrderItemMapper itemMapper;
    private final PaymentMapper paymentMapper;
    private final PurchaseInMapper purchaseMapper;

    public ReportService(SaleOrderMapper orderMapper, SaleOrderItemMapper itemMapper,
                         PaymentMapper paymentMapper, PurchaseInMapper purchaseMapper) {
        this.orderMapper = orderMapper;
        this.itemMapper = itemMapper;
        this.paymentMapper = paymentMapper;
        this.purchaseMapper = purchaseMapper;
    }

    public Map<String, Object> summary(LocalDate dateFrom, LocalDate dateTo) {
        List<SaleOrder> orders = orderMapper.selectList(new LambdaQueryWrapper<SaleOrder>()
                .ge(dateFrom != null, SaleOrder::getOrderDate, dateFrom)
                .le(dateTo != null, SaleOrder::getOrderDate, dateTo)
                .ne(SaleOrder::getStatus, "VOID")
                .orderByAsc(SaleOrder::getOrderDate));

        // 明细：金额/成本/销量 Top
        BigDecimal cost = BigDecimal.ZERO;
        Map<Long, Map<String, Object>> top = new LinkedHashMap<>();
        if (!orders.isEmpty()) {
            Set<Long> ids = new HashSet<>();
            orders.forEach(o -> ids.add(o.getId()));
            List<SaleOrderItem> items = itemMapper.selectList(new LambdaQueryWrapper<SaleOrderItem>()
                    .in(SaleOrderItem::getSaleId, ids));
            for (SaleOrderItem it : items) {
                BigDecimal itemCost = nvl(it.getQty()).multiply(nvl(it.getCostPrice()));
                cost = cost.add(itemCost);
                Map<String, Object> g = top.computeIfAbsent(it.getProductId(), k -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("productId", it.getProductId());
                    m.put("productName", it.getProductName());
                    m.put("spec", it.getSpec());
                    m.put("qty", BigDecimal.ZERO);
                    m.put("amount", BigDecimal.ZERO);
                    m.put("profit", BigDecimal.ZERO);
                    return m;
                });
                g.put("qty", ((BigDecimal) g.get("qty")).add(nvl(it.getQty())));
                g.put("amount", ((BigDecimal) g.get("amount")).add(nvl(it.getAmount())));
                g.put("profit", ((BigDecimal) g.get("profit")).add(nvl(it.getAmount()).subtract(itemCost)));
            }
        }

        BigDecimal salesAmount = BigDecimal.ZERO;
        BigDecimal cashAmount = BigDecimal.ZERO;
        BigDecimal creditAmount = BigDecimal.ZERO;
        Map<LocalDate, Map<String, Object>> byDay = new LinkedHashMap<>();
        for (SaleOrder o : orders) {
            BigDecimal amt = nvl(o.getTotalAmount());
            salesAmount = salesAmount.add(amt);
            if ("CASH".equals(o.getPayMethod())) {
                cashAmount = cashAmount.add(amt);
            } else {
                creditAmount = creditAmount.add(amt);
            }
            LocalDate d = o.getOrderDate() == null ? LocalDate.now() : o.getOrderDate();
            Map<String, Object> day = byDay.computeIfAbsent(d, k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("date", d.format(DATE));
                m.put("amount", BigDecimal.ZERO);
                m.put("cashAmount", BigDecimal.ZERO);
                m.put("creditAmount", BigDecimal.ZERO);
                m.put("count", 0L);
                return m;
            });
            day.put("amount", ((BigDecimal) day.get("amount")).add(amt));
            day.put("count", (Long) day.get("count") + 1);
            String key = "CASH".equals(o.getPayMethod()) ? "cashAmount" : "creditAmount";
            day.put(key, ((BigDecimal) day.get(key)).add(amt));
        }

        // 收款金额（区间内已登记收款）
        List<Payment> payments = paymentMapper.selectList(new LambdaQueryWrapper<Payment>()
                .ge(dateFrom != null, Payment::getPayDate, dateFrom)
                .le(dateTo != null, Payment::getPayDate, dateTo)
                .eq(Payment::getStatus, "REGISTERED"));
        BigDecimal paymentAmount = BigDecimal.ZERO;
        for (Payment p : payments) {
            paymentAmount = paymentAmount.add(nvl(p.getAmount()));
        }

        // 采购入库（区间内已入库）
        List<PurchaseIn> purchases = purchaseMapper.selectList(new LambdaQueryWrapper<PurchaseIn>()
                .ge(dateFrom != null, PurchaseIn::getOrderDate, dateFrom)
                .le(dateTo != null, PurchaseIn::getOrderDate, dateTo)
                .eq(PurchaseIn::getStatus, "STORED"));
        BigDecimal purchaseAmount = BigDecimal.ZERO;
        for (PurchaseIn p : purchases) {
            purchaseAmount = purchaseAmount.add(nvl(p.getTotalAmount()));
        }

        BigDecimal profit = salesAmount.subtract(cost);
        // 新增欠款 = 挂账销售额 - 收款金额
        BigDecimal newDebt = creditAmount.subtract(paymentAmount);

        List<Map<String, Object>> byDayList = new ArrayList<>(byDay.values());
        byDayList.forEach(m -> round(m, "amount", "cashAmount", "creditAmount"));

        List<Map<String, Object>> topList = new ArrayList<>(top.values());
        topList.forEach(m -> round(m, "qty", "amount", "profit"));
        topList.sort(Comparator.comparing((Map<String, Object> m) -> (BigDecimal) m.get("qty"),
                Comparator.reverseOrder()));
        if (topList.size() > 10) {
            topList = new ArrayList<>(topList.subList(0, 10));
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("orderCount", (long) orders.size());
        summary.put("salesAmount", salesAmount.setScale(2, RoundingMode.HALF_UP));
        summary.put("costAmount", cost.setScale(2, RoundingMode.HALF_UP));
        summary.put("profit", profit.setScale(2, RoundingMode.HALF_UP));
        summary.put("cashAmount", cashAmount.setScale(2, RoundingMode.HALF_UP));
        summary.put("creditAmount", creditAmount.setScale(2, RoundingMode.HALF_UP));
        summary.put("paymentAmount", paymentAmount.setScale(2, RoundingMode.HALF_UP));
        summary.put("newDebt", newDebt.setScale(2, RoundingMode.HALF_UP));
        summary.put("purchaseCount", (long) purchases.size());
        summary.put("purchaseAmount", purchaseAmount.setScale(2, RoundingMode.HALF_UP));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("dateFrom", dateFrom == null ? null : dateFrom.format(DATE));
        data.put("dateTo", dateTo == null ? null : dateTo.format(DATE));
        data.put("summary", summary);
        data.put("byDay", byDayList);
        data.put("topProducts", topList);
        return data;
    }

    private void round(Map<String, Object> m, String... keys) {
        for (String k : keys) {
            Object v = m.get(k);
            if (v instanceof BigDecimal b) {
                m.put(k, b.setScale(2, RoundingMode.HALF_UP));
            }
        }
    }

    private BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
