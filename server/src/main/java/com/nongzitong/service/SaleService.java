package com.nongzitong.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nongzitong.common.BizException;
import com.nongzitong.common.PageResult;
import com.nongzitong.controller.dto.SaleOrderRequest;
import com.nongzitong.entity.Customer;
import com.nongzitong.entity.PaymentAlloc;
import com.nongzitong.entity.Product;
import com.nongzitong.entity.SaleOrder;
import com.nongzitong.entity.SaleOrderItem;
import com.nongzitong.mapper.CustomerMapper;
import com.nongzitong.mapper.PaymentAllocMapper;
import com.nongzitong.mapper.SaleOrderItemMapper;
import com.nongzitong.mapper.SaleOrderMapper;
import com.nongzitong.security.LoginUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 销售服务：购物车式开单（库存校验、改价留痕）、流水查询、作废回滚。
 */
@Service
public class SaleService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final SaleOrderMapper orderMapper;
    private final SaleOrderItemMapper itemMapper;
    private final CustomerMapper customerMapper;
    private final PaymentAllocMapper allocMapper;
    private final StockService stockService;
    private final ProductService productService;
    private final AuthService authService;

    public SaleService(SaleOrderMapper orderMapper, SaleOrderItemMapper itemMapper,
                       CustomerMapper customerMapper, PaymentAllocMapper allocMapper,
                       StockService stockService, ProductService productService,
                       AuthService authService) {
        this.orderMapper = orderMapper;
        this.itemMapper = itemMapper;
        this.customerMapper = customerMapper;
        this.allocMapper = allocMapper;
        this.stockService = stockService;
        this.productService = productService;
        this.authService = authService;
    }

    /**
     * 开单：金额 = Σ(数量×售价)；现结 → 已结清；挂账 → 待收款；
     * 每行先做数据库层库存校验，不足则整体回滚并提示。
     */
    @Transactional
    public SaleOrder create(SaleOrderRequest req, LoginUser loginUser) {
        if (!"CASH".equals(req.getPayMethod()) && !"CREDIT".equals(req.getPayMethod())) {
            throw new BizException(400, "收款方式仅支持现结或挂账");
        }
        Customer customer = null;
        if ("CREDIT".equals(req.getPayMethod())) {
            if (req.getCustomerId() == null) {
                throw new BizException(400, "挂账必须选择赊销客户");
            }
            customer = customerMapper.selectById(req.getCustomerId());
            if (customer == null) {
                throw new BizException(404, "客户不存在");
            }
            if (customer.getStatus() != null && customer.getStatus() == 0) {
                throw new BizException(400, "客户「" + customer.getName() + "」已停用，不可挂账");
            }
        } else if (req.getCustomerId() != null) {
            customer = customerMapper.selectById(req.getCustomerId());
        }

        String operator = authService.displayName(loginUser);
        SaleOrder order = new SaleOrder();
        order.setOrderDate(req.getOrderDate() == null ? LocalDate.now() : req.getOrderDate());
        order.setPayMethod(req.getPayMethod());
        order.setCustomerId(customer == null ? null : customer.getId());
        order.setCustomerName(customer == null ? null : customer.getName());
        order.setRemark(req.getRemark());
        order.setCreatedBy(loginUser.getUserId());
        order.setCreatedByName(operator);
        order.setOrderNo(nextOrderNo(order.getOrderDate()));

        BigDecimal total = BigDecimal.ZERO;
        List<SaleOrderItem> items = new java.util.ArrayList<>();
        for (SaleOrderRequest.Item r : req.getItems()) {
            Product product = stockService.require(r.getProductId());
            if (product.getStatus() != null && product.getStatus() == 0) {
                throw new BizException(400, "商品「" + product.getName() + "」已停用，不可开单");
            }
            SaleOrderItem item = new SaleOrderItem();
            item.setProductId(product.getId());
            item.setProductName(product.getName());
            item.setSpec(product.getSpec());
            item.setQty(r.getQty());
            item.setPrice(r.getPrice());
            item.setAmount(r.getQty().multiply(r.getPrice()).setScale(2, RoundingMode.HALF_UP));
            item.setCostPrice(nvl(product.getPurchasePrice()));
            // 改价留痕：档案售价 vs 单据售价
            item.setOriginPrice(nvl(product.getSalePrice()));
            boolean changed = nvl(product.getSalePrice()).compareTo(r.getPrice()) != 0;
            item.setPriceOperator(changed ? operator : null);
            total = total.add(item.getAmount());
            items.add(item);
        }
        order.setTotalAmount(total);
        if ("CASH".equals(req.getPayMethod())) {
            order.setStatus("PAID");
            order.setReceivedAmount(total);
        } else {
            order.setStatus("UNPAID");
            order.setReceivedAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        }

        orderMapper.insert(order);
        for (SaleOrderItem item : items) {
            item.setSaleId(order.getId());
            itemMapper.insert(item);
            // 数据库层条件扣减：库存不足直接抛错，事务整体回滚
            stockService.deduct(item.getProductId(), item.getQty(), order.getOrderNo(), operator);
        }
        order.setItems(items);
        return order;
    }

    /** 销售流水分页：日期区间 + 客户/单号/商品模糊 + 状态 + 收款方式 */
    public PageResult<SaleOrder> page(LocalDate dateFrom, LocalDate dateTo, String keyword,
                                      String status, String payMethod, int page, int size) {
        LambdaQueryWrapper<SaleOrder> wrapper = new LambdaQueryWrapper<SaleOrder>()
                .ge(dateFrom != null, SaleOrder::getOrderDate, dateFrom)
                .le(dateTo != null, SaleOrder::getOrderDate, dateTo)
                .eq(StringUtils.hasText(status), SaleOrder::getStatus, status)
                .eq(StringUtils.hasText(payMethod), SaleOrder::getPayMethod, payMethod)
                .and(StringUtils.hasText(keyword), w -> w
                        .like(SaleOrder::getOrderNo, keyword)
                        .or().like(SaleOrder::getCustomerName, keyword)
                        .or().like(SaleOrder::getCreatedByName, keyword))
                .orderByDesc(SaleOrder::getOrderDate)
                .orderByDesc(SaleOrder::getId);
        IPage<SaleOrder> result = orderMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.of(result.getTotal(), result.getRecords());
    }

    /** 销售单详情（含明细） */
    public SaleOrder get(Long id) {
        SaleOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw new BizException(404, "销售单不存在");
        }
        order.setItems(itemMapper.selectList(new LambdaQueryWrapper<SaleOrderItem>()
                .eq(SaleOrderItem::getSaleId, id).orderByAsc(SaleOrderItem::getId)));
        return order;
    }

    /**
     * 作废：库存回滚；挂账欠款随状态失效自动回滚；已分摊的收款释放为预存。
     */
    @Transactional
    public void voidOrder(Long id, String reason, LoginUser loginUser) {
        SaleOrder order = get(id);
        if ("VOID".equals(order.getStatus())) {
            throw new BizException(400, "该销售单已作废");
        }
        String operator = authService.displayName(loginUser);
        for (SaleOrderItem item : order.getItems()) {
            stockService.rollback(item.getProductId(), item.getQty(), order.getOrderNo(), operator);
        }
        // 释放收款分摊（收款记录保留，金额转为预存）
        allocMapper.delete(new LambdaQueryWrapper<PaymentAlloc>().eq(PaymentAlloc::getSaleId, id));

        order.setStatus("VOID");
        order.setVoidReason(reason);
        order.setVoidedByName(operator);
        order.setVoidedAt(LocalDateTime.now());
        orderMapper.updateById(order);
    }

    /** 单号：XS + yyyyMMdd + 3 位当日流水 */
    private String nextOrderNo(LocalDate date) {
        String prefix = "XS" + date.format(DAY);
        Long count = orderMapper.selectCount(new LambdaQueryWrapper<SaleOrder>()
                .likeRight(SaleOrder::getOrderNo, prefix));
        return prefix + String.format("%03d", count + 1);
    }

    private BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
