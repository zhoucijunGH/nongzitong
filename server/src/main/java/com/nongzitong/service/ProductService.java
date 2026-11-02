package com.nongzitong.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nongzitong.common.BizException;
import com.nongzitong.controller.dto.ProductRequest;
import com.nongzitong.entity.Product;
import com.nongzitong.entity.StockFlow;
import com.nongzitong.mapper.ProductMapper;
import com.nongzitong.mapper.StockFlowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品档案服务：查询 / 维护（维护仅管理员）。
 */
@Service
public class ProductService {

    private final ProductMapper productMapper;
    private final StockFlowMapper flowMapper;

    public ProductService(ProductMapper productMapper, StockFlowMapper flowMapper) {
        this.productMapper = productMapper;
        this.flowMapper = flowMapper;
    }

    /** 商品列表：名称/规格模糊 + 类别 + 状态过滤 */
    public List<Product> list(String keyword, String category, Integer status) {
        return productMapper.selectList(new LambdaQueryWrapper<Product>()
                .eq(status != null, Product::getStatus, status)
                .eq(StringUtils.hasText(category), Product::getCategory, category)
                .and(StringUtils.hasText(keyword), w -> w
                        .like(Product::getName, keyword)
                        .or().like(Product::getSpec, keyword))
                .orderByDesc(Product::getId));
    }

    /** 新增商品：期初库存落库并生成一条期初入库流水 */
    @Transactional
    public Product create(ProductRequest req, String operator) {
        Long dup = productMapper.selectCount(new LambdaQueryWrapper<Product>()
                .eq(Product::getName, req.getName().trim())
                .eq(Product::getSpec, req.getSpec().trim()));
        if (dup != null && dup > 0) {
            throw new BizException(400, "已存在同名同规格商品，请检查档案");
        }
        Product product = new Product();
        apply(product, req);
        BigDecimal opening = req.getOpeningStock() == null ? BigDecimal.ZERO : req.getOpeningStock();
        product.setOpeningStock(opening);
        product.setStock(opening);
        product.setStatus(req.getStatus() == null ? 1 : req.getStatus());
        productMapper.insert(product);

        if (opening.compareTo(BigDecimal.ZERO) > 0) {
            StockFlow flow = new StockFlow();
            flow.setProductId(product.getId());
            flow.setProductName(product.getName());
            flow.setSpec(product.getSpec());
            flow.setFlowType("IN");
            flow.setDelta(opening);
            flow.setBalance(opening);
            flow.setRefNo("QC" + LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")));
            flow.setOperator(operator);
            flow.setCreatedAt(LocalDateTime.now());
            flowMapper.insert(flow);
        }
        return product;
    }

    /** 修改商品：期初库存不改，当前库存只能通过单据变动 */
    public Product update(Long id, ProductRequest req) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BizException(404, "商品不存在");
        }
        Long dup = productMapper.selectCount(new LambdaQueryWrapper<Product>()
                .eq(Product::getName, req.getName().trim())
                .eq(Product::getSpec, req.getSpec().trim())
                .ne(Product::getId, id));
        if (dup != null && dup > 0) {
            throw new BizException(400, "已存在同名同规格商品，请检查档案");
        }
        apply(product, req);
        if (req.getStatus() != null) {
            product.setStatus(req.getStatus());
        }
        productMapper.updateById(product);
        return product;
    }

    /** 删除商品档案（历史单据保留冗余快照） */
    public void delete(Long id) {
        if (productMapper.selectById(id) == null) {
            throw new BizException(404, "商品不存在");
        }
        productMapper.deleteById(id);
    }

    private void apply(Product product, ProductRequest req) {
        product.setName(req.getName().trim());
        product.setSpec(req.getSpec().trim());
        product.setUnit(req.getUnit().trim());
        product.setCategory(req.getCategory());
        product.setPurchasePrice(nvl(req.getPurchasePrice()));
        product.setSalePrice(nvl(req.getSalePrice()));
        product.setWarningStock(nvl(req.getWarningStock()));
        if (req.getRemark() != null) {
            product.setRemark(req.getRemark());
        }
    }

    private BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
