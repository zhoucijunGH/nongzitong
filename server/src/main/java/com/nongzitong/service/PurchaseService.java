package com.nongzitong.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nongzitong.common.BizException;
import com.nongzitong.common.PageResult;
import com.nongzitong.controller.dto.PurchaseInRequest;
import com.nongzitong.entity.Product;
import com.nongzitong.entity.PurchaseIn;
import com.nongzitong.entity.PurchaseInItem;
import com.nongzitong.entity.Supplier;
import com.nongzitong.mapper.PurchaseInItemMapper;
import com.nongzitong.mapper.PurchaseInMapper;
import com.nongzitong.mapper.SupplierMapper;
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
 * 采购入库服务：保存即库存增加；作废回滚（负库存需二次确认）。
 */
@Service
public class PurchaseService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final PurchaseInMapper inMapper;
    private final PurchaseInItemMapper itemMapper;
    private final SupplierMapper supplierMapper;
    private final StockService stockService;
    private final ProductService productService;
    private final AuthService authService;

    public PurchaseService(PurchaseInMapper inMapper, PurchaseInItemMapper itemMapper,
                           SupplierMapper supplierMapper, StockService stockService,
                           ProductService productService, AuthService authService) {
        this.inMapper = inMapper;
        this.itemMapper = itemMapper;
        this.supplierMapper = supplierMapper;
        this.stockService = stockService;
        this.productService = productService;
        this.authService = authService;
    }

    /** 采购入库：服务端算单号/金额，保存即库存 +，并生成入库流水 */
    @Transactional
    public PurchaseIn create(PurchaseInRequest req, LoginUser loginUser) {
        Supplier supplier = supplierMapper.selectById(req.getSupplierId());
        if (supplier == null) {
            throw new BizException(404, "供应商不存在");
        }
        String operator = authService.displayName(loginUser);

        PurchaseIn in = new PurchaseIn();
        in.setOrderDate(req.getOrderDate() == null ? LocalDate.now() : req.getOrderDate());
        in.setSupplierId(supplier.getId());
        in.setSupplierName(supplier.getName());
        in.setRemark(req.getRemark());
        in.setStatus("STORED");
        in.setCreatedBy(loginUser.getUserId());
        in.setCreatedByName(operator);
        in.setOrderNo(nextOrderNo(in.getOrderDate()));

        BigDecimal total = BigDecimal.ZERO;
        List<PurchaseInItem> items = new java.util.ArrayList<>();
        for (PurchaseInRequest.Item r : req.getItems()) {
            Product product = stockService.require(r.getProductId());
            if (product.getStatus() != null && product.getStatus() == 0) {
                throw new BizException(400, "商品「" + product.getName() + "」已停用，不可入库");
            }
            PurchaseInItem item = new PurchaseInItem();
            item.setInId(in.getId());
            item.setProductId(product.getId());
            item.setProductName(product.getName());
            item.setSpec(product.getSpec());
            item.setQty(r.getQty());
            item.setPrice(r.getPrice());
            item.setAmount(r.getQty().multiply(r.getPrice()).setScale(2, RoundingMode.HALF_UP));
            total = total.add(item.getAmount());
            items.add(item);
        }
        in.setTotalAmount(total);
        inMapper.insert(in);

        for (PurchaseInItem item : items) {
            item.setInId(in.getId());
            itemMapper.insert(item);
            stockService.increase(item.getProductId(), item.getQty(), in.getOrderNo(), operator);
        }
        in.setItems(items);
        return in;
    }

    /** 采购入库分页：日期区间 + 供应商/单号模糊 + 状态 */
    public PageResult<PurchaseIn> page(LocalDate dateFrom, LocalDate dateTo, String keyword,
                                       String status, int page, int size) {
        LambdaQueryWrapper<PurchaseIn> wrapper = new LambdaQueryWrapper<PurchaseIn>()
                .ge(dateFrom != null, PurchaseIn::getOrderDate, dateFrom)
                .le(dateTo != null, PurchaseIn::getOrderDate, dateTo)
                .eq(StringUtils.hasText(status), PurchaseIn::getStatus, status)
                .and(StringUtils.hasText(keyword), w -> w
                        .like(PurchaseIn::getOrderNo, keyword)
                        .or().like(PurchaseIn::getSupplierName, keyword))
                .orderByDesc(PurchaseIn::getOrderDate)
                .orderByDesc(PurchaseIn::getId);
        IPage<PurchaseIn> result = inMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.of(result.getTotal(), result.getRecords());
    }

    /** 采购单详情（含明细） */
    public PurchaseIn get(Long id) {
        PurchaseIn in = inMapper.selectById(id);
        if (in == null) {
            throw new BizException(404, "采购单不存在");
        }
        in.setItems(itemMapper.selectList(new LambdaQueryWrapper<PurchaseInItem>()
                .eq(PurchaseInItem::getInId, id).orderByAsc(PurchaseInItem::getId)));
        return in;
    }

    /**
     * 作废：库存回滚；回滚会导致负库存时要求 confirm=true（前端警示确认）。
     */
    @Transactional
    public void voidOrder(Long id, String reason, Boolean confirm, LoginUser loginUser) {
        PurchaseIn in = get(id);
        if ("VOID".equals(in.getStatus())) {
            throw new BizException(400, "该采购单已作废");
        }
        List<String> negatives = new java.util.ArrayList<>();
        for (PurchaseInItem item : in.getItems()) {
            Product product = stockService.require(item.getProductId());
            BigDecimal after = nvl(product.getStock()).subtract(item.getQty());
            if (after.compareTo(BigDecimal.ZERO) < 0) {
                negatives.add("「" + product.getName() + " " + product.getSpec() + "」将为 "
                        + after.stripTrailingZeros().toPlainString());
            }
        }
        if (!negatives.isEmpty() && !Boolean.TRUE.equals(confirm)) {
            throw new BizException(409, "作废将导致负库存：" + String.join("、", negatives) + "，请确认后重试");
        }

        String operator = authService.displayName(loginUser);
        for (PurchaseInItem item : in.getItems()) {
            stockService.rollback(item.getProductId(), item.getQty().negate(), in.getOrderNo(), operator);
        }
        in.setStatus("VOID");
        in.setVoidReason(reason);
        in.setVoidedByName(operator);
        in.setVoidedAt(LocalDateTime.now());
        inMapper.updateById(in);
    }

    /** 单号：RK + yyyyMMdd + 3 位当日流水 */
    private String nextOrderNo(LocalDate date) {
        String prefix = "RK" + date.format(DAY);
        Long count = inMapper.selectCount(new LambdaQueryWrapper<PurchaseIn>()
                .likeRight(PurchaseIn::getOrderNo, prefix));
        return prefix + String.format("%03d", count + 1);
    }

    private BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
