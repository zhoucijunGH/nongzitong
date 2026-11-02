package com.nongzitong.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nongzitong.common.BizException;
import com.nongzitong.common.PageResult;
import com.nongzitong.controller.dto.PaymentRequest;
import com.nongzitong.entity.Customer;
import com.nongzitong.entity.Payment;
import com.nongzitong.entity.PaymentAlloc;
import com.nongzitong.entity.SaleOrder;
import com.nongzitong.mapper.CustomerMapper;
import com.nongzitong.mapper.PaymentAllocMapper;
import com.nongzitong.mapper.PaymentMapper;
import com.nongzitong.mapper.SaleOrderMapper;
import com.nongzitong.security.LoginUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 收款登记服务：分摊到待收款销售单（可部分收款），只可作废不可修改。
 */
@Service
public class PaymentService {

    private final PaymentMapper paymentMapper;
    private final PaymentAllocMapper allocMapper;
    private final SaleOrderMapper orderMapper;
    private final CustomerMapper customerMapper;
    private final AuthService authService;

    public PaymentService(PaymentMapper paymentMapper, PaymentAllocMapper allocMapper,
                          SaleOrderMapper orderMapper, CustomerMapper customerMapper,
                          AuthService authService) {
        this.paymentMapper = paymentMapper;
        this.allocMapper = allocMapper;
        this.orderMapper = orderMapper;
        this.customerMapper = customerMapper;
        this.authService = authService;
    }

    /**
     * 收款登记：指定 saleId 时优先摊该单；否则按开单顺序 FIFO 摊到该客户全部待收款单。
     * 超出欠款部分不摊销（视为预存），收款金额仍全额入账。
     */
    @Transactional
    public Payment create(PaymentRequest req, LoginUser loginUser) {
        Customer customer = customerMapper.selectById(req.getCustomerId());
        if (customer == null) {
            throw new BizException(404, "客户不存在");
        }
        String operator = authService.displayName(loginUser);

        Payment payment = new Payment();
        payment.setCustomerId(customer.getId());
        payment.setCustomerName(customer.getName());
        payment.setAmount(req.getAmount().setScale(2, RoundingMode.HALF_UP));
        payment.setMethod(req.getMethod());
        payment.setOperator(operator);
        payment.setPayDate(req.getPayDate() == null ? LocalDate.now() : req.getPayDate());
        payment.setRemark(req.getRemark());
        payment.setStatus("REGISTERED");
        payment.setCreatedBy(loginUser.getUserId());
        payment.setCreatedByName(operator);
        paymentMapper.insert(payment);

        List<SaleOrder> targets;
        if (req.getSaleId() != null) {
            SaleOrder order = orderMapper.selectById(req.getSaleId());
            if (order == null || !customer.getId().equals(order.getCustomerId())) {
                throw new BizException(404, "销售单不存在或不属于该客户");
            }
            if (!"UNPAID".equals(order.getStatus())) {
                throw new BizException(400, "该销售单不是待收款状态，无法收款");
            }
            targets = List.of(order);
        } else {
            targets = orderMapper.selectList(new LambdaQueryWrapper<SaleOrder>()
                    .eq(SaleOrder::getCustomerId, customer.getId())
                    .eq(SaleOrder::getStatus, "UNPAID")
                    .orderByAsc(SaleOrder::getId));
        }

        BigDecimal remain = payment.getAmount();
        for (SaleOrder order : targets) {
            if (remain.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            BigDecimal debt = nvl(order.getTotalAmount()).subtract(nvl(order.getReceivedAmount()));
            if (debt.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal use = debt.min(remain);

            PaymentAlloc alloc = new PaymentAlloc();
            alloc.setPaymentId(payment.getId());
            alloc.setSaleId(order.getId());
            alloc.setAmount(use);
            allocMapper.insert(alloc);

            BigDecimal received = nvl(order.getReceivedAmount()).add(use);
            order.setReceivedAmount(received.setScale(2, RoundingMode.HALF_UP));
            if (received.compareTo(nvl(order.getTotalAmount())) >= 0) {
                order.setStatus("PAID");
            }
            orderMapper.updateById(order);
            remain = remain.subtract(use);
        }
        return payment;
    }

    /** 收款记录分页 */
    public PageResult<Payment> page(LocalDate dateFrom, LocalDate dateTo, String keyword,
                                    String status, int page, int size) {
        LambdaQueryWrapper<Payment> wrapper = new LambdaQueryWrapper<Payment>()
                .ge(dateFrom != null, Payment::getPayDate, dateFrom)
                .le(dateTo != null, Payment::getPayDate, dateTo)
                .eq(StringUtils.hasText(status), Payment::getStatus, status)
                .and(StringUtils.hasText(keyword), w -> w
                        .like(Payment::getCustomerName, keyword)
                        .or().like(Payment::getOperator, keyword))
                .orderByDesc(Payment::getId);
        IPage<Payment> result = paymentMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.of(result.getTotal(), result.getRecords());
    }

    /**
     * 收款作废：按分摊逐单回滚实收（已结清回退待收款），余额回滚。
     */
    @Transactional
    public void voidPayment(Long id, String reason, LoginUser loginUser) {
        Payment payment = paymentMapper.selectById(id);
        if (payment == null) {
            throw new BizException(404, "收款记录不存在");
        }
        if ("VOID".equals(payment.getStatus())) {
            throw new BizException(400, "该收款记录已作废");
        }
        List<PaymentAlloc> allocs = allocMapper.selectList(new LambdaQueryWrapper<PaymentAlloc>()
                .eq(PaymentAlloc::getPaymentId, id).orderByDesc(PaymentAlloc::getId));
        for (PaymentAlloc alloc : allocs) {
            SaleOrder order = orderMapper.selectById(alloc.getSaleId());
            if (order != null && !"VOID".equals(order.getStatus())) {
                BigDecimal received = nvl(order.getReceivedAmount()).subtract(alloc.getAmount());
                order.setReceivedAmount(received.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP));
                if (received.compareTo(nvl(order.getTotalAmount())) < 0 && "PAID".equals(order.getStatus())) {
                    order.setStatus("UNPAID");
                }
                orderMapper.updateById(order);
            }
            allocMapper.deleteById(alloc.getId());
        }
        payment.setStatus("VOID");
        payment.setVoidReason(reason);
        payment.setVoidedByName(authService.displayName(loginUser));
        payment.setVoidedAt(LocalDateTime.now());
        paymentMapper.updateById(payment);
    }

    private BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
