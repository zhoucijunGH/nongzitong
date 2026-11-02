package com.nongzitong.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nongzitong.common.BizException;
import com.nongzitong.common.PageResult;
import com.nongzitong.entity.Product;
import com.nongzitong.entity.StockFlow;
import com.nongzitong.mapper.ProductMapper;
import com.nongzitong.mapper.StockFlowMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 库存服务：当前库存查询、库存流水、库存增减（含负库存校验）。
 */
@Service
public class StockService {

    private final ProductMapper productMapper;
    private final StockFlowMapper flowMapper;

    public StockService(ProductMapper productMapper, StockFlowMapper flowMapper) {
        this.productMapper = productMapper;
        this.flowMapper = flowMapper;
    }

    /** 当前库存表：商品/规格/库存/预警/低库存标记 */
    public List<Product> stockList(String keyword, String category, Boolean lowOnly) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<Product>()
                .eq(StringUtils.hasText(category), Product::getCategory, category)
                .and(StringUtils.hasText(keyword), w -> w
                        .like(Product::getName, keyword)
                        .or().like(Product::getSpec, keyword))
                .orderByAsc(Product::getCategory)
                .orderByAsc(Product::getId);
        List<Product> list = productMapper.selectList(wrapper);
        if (Boolean.TRUE.equals(lowOnly)) {
            list.removeIf(p -> !isLow(p));
        }
        return list;
    }

    /** 是否低库存（启用且设置了预警值且当前库存低于预警） */
    public boolean isLow(Product p) {
        return p.getWarningStock() != null && p.getWarningStock().compareTo(BigDecimal.ZERO) > 0
                && p.getStock() != null && p.getStock().compareTo(p.getWarningStock()) < 0;
    }

    /** 库存流水分页（只增不改） */
    public PageResult<StockFlow> flowPage(String keyword, String flowType,
                                          java.time.LocalDate dateFrom, java.time.LocalDate dateTo,
                                          int page, int size) {
        LambdaQueryWrapper<StockFlow> wrapper = new LambdaQueryWrapper<StockFlow>()
                .eq(StringUtils.hasText(flowType), StockFlow::getFlowType, flowType)
                .ge(dateFrom != null, StockFlow::getCreatedAt, dateFrom != null ? dateFrom.atStartOfDay() : null)
                .le(dateTo != null, StockFlow::getCreatedAt, dateTo != null ? dateTo.plusDays(1).atStartOfDay() : null)
                .and(StringUtils.hasText(keyword), w -> w
                        .like(StockFlow::getProductName, keyword)
                        .or().like(StockFlow::getRefNo, keyword))
                .orderByDesc(StockFlow::getId);
        IPage<StockFlow> result = flowMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.of(result.getTotal(), result.getRecords());
    }

    /**
     * 扣减库存（销售出库）：数据库层条件更新保证不出现负库存。
     */
    public void deduct(Long productId, BigDecimal qty, String refNo, String operator) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BizException(404, "商品不存在");
        }
        int rows = productMapper.deductStock(productId, qty);
        if (rows == 0) {
            throw new BizException(400, "商品「" + product.getName() + " " + product.getSpec()
                    + "」库存不足（当前 " + trim(product.getStock()) + "，需 " + trim(qty) + "），已阻止超卖");
        }
        writeFlow(product, "OUT", qty.negate(), refNo, operator);
    }

    /** 增加库存（采购入库） */
    public void increase(Long productId, BigDecimal qty, String refNo, String operator) {
        Product product = require(productId);
        productMapper.addStock(productId, qty);
        writeFlow(product, "IN", qty, refNo, operator);
    }

    /** 作废回滚库存（正负由 delta 决定） */
    public void rollback(Long productId, BigDecimal delta, String refNo, String operator) {
        Product product = require(productId);
        if (delta.compareTo(BigDecimal.ZERO) < 0) {
            productMapper.forceDeductStock(productId, delta.negate());
        } else {
            productMapper.addStock(productId, delta);
        }
        writeFlow(product, "VOID", delta, refNo, operator);
    }

    public Product require(Long productId) {
        Product product = productId == null ? null : productMapper.selectById(productId);
        if (product == null) {
            throw new BizException(404, "商品不存在");
        }
        return product;
    }

    /** 写库存流水（只增不改），并记录变化后余额 */
    private void writeFlow(Product product, String flowType, BigDecimal delta, String refNo, String operator) {
        Product fresh = productMapper.selectById(product.getId());
        StockFlow flow = new StockFlow();
        flow.setProductId(product.getId());
        flow.setProductName(product.getName());
        flow.setSpec(product.getSpec());
        flow.setFlowType(flowType);
        flow.setDelta(delta);
        flow.setBalance(fresh == null ? null : fresh.getStock());
        flow.setRefNo(refNo);
        flow.setOperator(operator);
        flow.setCreatedAt(LocalDateTime.now());
        flowMapper.insert(flow);
    }

    private BigDecimal trim(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v.stripTrailingZeros();
    }
}
