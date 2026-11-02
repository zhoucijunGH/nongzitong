package com.nongzitong.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nongzitong.entity.Product;
import com.nongzitong.entity.SaleOrder;
import com.nongzitong.entity.SaleOrderItem;
import com.nongzitong.mapper.ProductMapper;
import com.nongzitong.mapper.SaleOrderItemMapper;
import com.nongzitong.mapper.SaleOrderMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 工作台服务：今日销售额/毛利/开单数、低库存数、欠款总额、近 7 日销售趋势。
 */
@Service
public class DashboardService {

    private static final DateTimeFormatter MD = DateTimeFormatter.ofPattern("MM-dd");

    private final SaleOrderMapper orderMapper;
    private final SaleOrderItemMapper itemMapper;
    private final ProductMapper productMapper;
    private final StockService stockService;

    public DashboardService(SaleOrderMapper orderMapper, SaleOrderItemMapper itemMapper,
                            ProductMapper productMapper, StockService stockService) {
        this.orderMapper = orderMapper;
        this.itemMapper = itemMapper;
        this.productMapper = productMapper;
        this.stockService = stockService;
    }

    public Map<String, Object> dashboard() {
        LocalDate today = LocalDate.now();

        // 今日销售（不含作废）
        List<SaleOrder> todayOrders = orderMapper.selectList(new LambdaQueryWrapper<SaleOrder>()
                .eq(SaleOrder::getOrderDate, today)
                .ne(SaleOrder::getStatus, "VOID"));
        BigDecimal todayAmount = BigDecimal.ZERO;
        for (SaleOrder o : todayOrders) {
            todayAmount = todayAmount.add(nvl(o.getTotalAmount()));
        }
        BigDecimal todayProfit = profitOf(todayOrders);

        // 欠款总额 = 全部待收款单未收金额之和
        List<SaleOrder> unpaid = orderMapper.selectList(new LambdaQueryWrapper<SaleOrder>()
                .eq(SaleOrder::getStatus, "UNPAID"));
        BigDecimal debtTotal = BigDecimal.ZERO;
        for (SaleOrder o : unpaid) {
            debtTotal = debtTotal.add(nvl(o.getTotalAmount()).subtract(nvl(o.getReceivedAmount())));
        }

        // 低库存商品数
        List<Product> products = productMapper.selectList(new LambdaQueryWrapper<Product>()
                .eq(Product::getStatus, 1));
        List<Map<String, Object>> lowList = new ArrayList<>();
        for (Product p : products) {
            if (stockService.isLow(p)) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", p.getId());
                m.put("name", p.getName());
                m.put("spec", p.getSpec());
                m.put("unit", p.getUnit());
                m.put("stock", p.getStock());
                m.put("warningStock", p.getWarningStock());
                lowList.add(m);
            }
        }

        // 近 7 天销售趋势（无单日期补 0）
        LocalDate start = today.minusDays(6);
        List<SaleOrder> week = orderMapper.selectList(new LambdaQueryWrapper<SaleOrder>()
                .ge(SaleOrder::getOrderDate, start)
                .le(SaleOrder::getOrderDate, today)
                .ne(SaleOrder::getStatus, "VOID"));
        Map<LocalDate, BigDecimal> byDate = new LinkedHashMap<>();
        for (SaleOrder o : week) {
            byDate.merge(o.getOrderDate(), nvl(o.getTotalAmount()), BigDecimal::add);
        }
        List<Map<String, Object>> trend = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate d = start.plusDays(i);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", d.format(MD));
            item.put("amount", byDate.getOrDefault(d, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP));
            trend.add(item);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("todayAmount", todayAmount.setScale(2, RoundingMode.HALF_UP));
        data.put("todayProfit", todayProfit.setScale(2, RoundingMode.HALF_UP));
        data.put("todayCount", (long) todayOrders.size());
        data.put("lowStockCount", (long) lowList.size());
        data.put("lowStockList", lowList);
        data.put("debtTotal", debtTotal.setScale(2, RoundingMode.HALF_UP));
        data.put("trend", trend);
        return data;
    }

    /** 毛利 = 销售额 - Σ(数量 × 单据进价) */
    private BigDecimal profitOf(List<SaleOrder> orders) {
        if (orders.isEmpty()) {
            return BigDecimal.ZERO;
        }
        Set<Long> ids = new HashSet<>();
        orders.forEach(o -> ids.add(o.getId()));
        List<SaleOrderItem> items = itemMapper.selectList(new LambdaQueryWrapper<SaleOrderItem>()
                .in(SaleOrderItem::getSaleId, ids));
        BigDecimal sales = BigDecimal.ZERO;
        BigDecimal cost = BigDecimal.ZERO;
        for (SaleOrderItem it : items) {
            sales = sales.add(nvl(it.getAmount()));
            cost = cost.add(nvl(it.getQty()).multiply(nvl(it.getCostPrice())));
        }
        return sales.subtract(cost);
    }

    private BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
