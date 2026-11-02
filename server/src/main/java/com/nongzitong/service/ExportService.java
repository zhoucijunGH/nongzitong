package com.nongzitong.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nongzitong.entity.Customer;
import com.nongzitong.entity.Payment;
import com.nongzitong.entity.Product;
import com.nongzitong.entity.PurchaseIn;
import com.nongzitong.entity.PurchaseInItem;
import com.nongzitong.entity.SaleOrder;
import com.nongzitong.entity.SaleOrderItem;
import com.nongzitong.mapper.CustomerMapper;
import com.nongzitong.mapper.PaymentMapper;
import com.nongzitong.mapper.ProductMapper;
import com.nongzitong.mapper.PurchaseInItemMapper;
import com.nongzitong.mapper.PurchaseInMapper;
import com.nongzitong.mapper.SaleOrderItemMapper;
import com.nongzitong.mapper.SaleOrderMapper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
 * 导出服务：销售/采购/库存/欠款/经营报表 → xlsx 文件流（Apache POI）。
 */
@Service
public class ExportService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final SaleOrderMapper orderMapper;
    private final SaleOrderItemMapper saleItemMapper;
    private final PurchaseInMapper inMapper;
    private final PurchaseInItemMapper inItemMapper;
    private final ProductMapper productMapper;
    private final CustomerMapper customerMapper;
    private final PaymentMapper paymentMapper;
    private final DebtService debtService;
    private final ReportService reportService;

    public ExportService(SaleOrderMapper orderMapper, SaleOrderItemMapper saleItemMapper,
                         PurchaseInMapper inMapper, PurchaseInItemMapper inItemMapper,
                         ProductMapper productMapper, CustomerMapper customerMapper,
                         PaymentMapper paymentMapper, DebtService debtService,
                         ReportService reportService) {
        this.orderMapper = orderMapper;
        this.saleItemMapper = saleItemMapper;
        this.inMapper = inMapper;
        this.inItemMapper = inItemMapper;
        this.productMapper = productMapper;
        this.customerMapper = customerMapper;
        this.paymentMapper = paymentMapper;
        this.debtService = debtService;
        this.reportService = reportService;
    }

    /** 销售流水导出（含明细行） */
    public byte[] exportSales(LocalDate dateFrom, LocalDate dateTo) throws IOException {
        List<SaleOrder> orders = orderMapper.selectList(new LambdaQueryWrapper<SaleOrder>()
                .ge(dateFrom != null, SaleOrder::getOrderDate, dateFrom)
                .le(dateTo != null, SaleOrder::getOrderDate, dateTo)
                .orderByDesc(SaleOrder::getOrderDate)
                .orderByDesc(SaleOrder::getId));
        Map<Long, List<SaleOrderItem>> itemsMap = itemsOf(orders);

        String[] headers = {"单号", "日期", "商品", "规格", "数量", "售价", "小计", "单据进价",
                "收款方式", "客户", "合计金额", "实收金额", "状态", "开单人", "备注"};
        List<List<Object>> rows = new ArrayList<>();
        for (SaleOrder o : orders) {
            List<SaleOrderItem> items = itemsMap.getOrDefault(o.getId(), List.of());
            if (items.isEmpty()) {
                rows.add(row(o.getOrderNo(), date(o.getOrderDate()), "", "", "", "", "", "",
                        payMethod(o.getPayMethod()), nvl(o.getCustomerName()), num(o.getTotalAmount()),
                        num(o.getReceivedAmount()), status(o.getStatus()), nvl(o.getCreatedByName()), nvl(o.getRemark())));
                continue;
            }
            for (SaleOrderItem it : items) {
                rows.add(row(o.getOrderNo(), date(o.getOrderDate()), it.getProductName(), it.getSpec(),
                        num(it.getQty()), num(it.getPrice()), num(it.getAmount()), num(it.getCostPrice()),
                        payMethod(o.getPayMethod()), nvl(o.getCustomerName()), num(o.getTotalAmount()),
                        num(o.getReceivedAmount()), status(o.getStatus()), nvl(o.getCreatedByName()), nvl(o.getRemark())));
            }
        }
        return build("销售流水", headers, rows);
    }

    /** 采购入库导出（含明细行） */
    public byte[] exportPurchases(LocalDate dateFrom, LocalDate dateTo) throws IOException {
        List<PurchaseIn> orders = inMapper.selectList(new LambdaQueryWrapper<PurchaseIn>()
                .ge(dateFrom != null, PurchaseIn::getOrderDate, dateFrom)
                .le(dateTo != null, PurchaseIn::getOrderDate, dateTo)
                .orderByDesc(PurchaseIn::getOrderDate)
                .orderByDesc(PurchaseIn::getId));
        Map<Long, List<PurchaseInItem>> itemsMap = new LinkedHashMap<>();
        if (!orders.isEmpty()) {
            Set<Long> ids = new HashSet<>();
            orders.forEach(o -> ids.add(o.getId()));
            for (PurchaseInItem it : inItemMapper.selectList(new LambdaQueryWrapper<PurchaseInItem>()
                    .in(PurchaseInItem::getInId, ids))) {
                itemsMap.computeIfAbsent(it.getInId(), k -> new ArrayList<>()).add(it);
            }
        }
        String[] headers = {"单号", "日期", "商品", "规格", "数量", "进价", "金额", "供应商",
                "总金额", "状态", "制单人", "备注"};
        List<List<Object>> rows = new ArrayList<>();
        for (PurchaseIn o : orders) {
            for (PurchaseInItem it : itemsMap.getOrDefault(o.getId(), List.of())) {
                rows.add(row(o.getOrderNo(), date(o.getOrderDate()), it.getProductName(), it.getSpec(),
                        num(it.getQty()), num(it.getPrice()), num(it.getAmount()), nvl(o.getSupplierName()),
                        num(o.getTotalAmount()), "STORED".equals(o.getStatus()) ? "已入库" : "已作废",
                        nvl(o.getCreatedByName()), nvl(o.getRemark())));
            }
        }
        return build("采购入库", headers, rows);
    }

    /** 当前库存导出 */
    public byte[] exportStock() throws IOException {
        List<Product> products = productMapper.selectList(new LambdaQueryWrapper<Product>()
                .orderByAsc(Product::getCategory).orderByAsc(Product::getId));
        String[] headers = {"商品", "规格", "单位", "类别", "进价", "售价", "期初库存",
                "当前库存", "预警库存", "低库存", "状态"};
        List<List<Object>> rows = new ArrayList<>();
        for (Product p : products) {
            boolean low = p.getWarningStock() != null && p.getWarningStock().compareTo(BigDecimal.ZERO) > 0
                    && p.getStock() != null && p.getStock().compareTo(p.getWarningStock()) < 0;
            rows.add(row(p.getName(), p.getSpec(), p.getUnit(), p.getCategory(),
                    num(p.getPurchasePrice()), num(p.getSalePrice()), num(p.getOpeningStock()),
                    num(p.getStock()), num(p.getWarningStock()), low ? "是" : "否",
                    p.getStatus() != null && p.getStatus() == 1 ? "启用" : "停用"));
        }
        return build("当前库存", headers, rows);
    }

    /** 客户欠款导出 */
    public byte[] exportDebts(String keyword) throws IOException {
        String[] headers = {"客户", "电话", "地址", "未结单数", "欠款余额", "累计收款", "最近欠款日期"};
        List<List<Object>> rows = new ArrayList<>();
        for (Map<String, Object> g : debtService.list(keyword)) {
            rows.add(row(g.get("customerName"), g.get("phone"), g.get("address"), g.get("orderCount"),
                    g.get("debtAmount"), g.get("paidAmount"), g.get("lastDate")));
        }
        return build("客户欠款", headers, rows);
    }

    /** 经营报表导出：汇总 + 按日 + 商品销量 Top */
    public byte[] exportReport(LocalDate dateFrom, LocalDate dateTo) throws IOException {
        Map<String, Object> data = reportService.summary(dateFrom, dateTo);
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) data.get("summary");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> byDay = (List<Map<String, Object>>) data.get("byDay");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> top = (List<Map<String, Object>>) data.get("topProducts");

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            CellStyle headStyle = headStyle(workbook);

            Sheet s1 = workbook.createSheet("汇总");
            writeHead(s1, headStyle, "指标", "数值");
            int i = 0;
            String[][] metrics = {
                    {"开单数", "orderCount"}, {"销售额", "salesAmount"}, {"成本", "costAmount"},
                    {"毛利", "profit"}, {"现结金额", "cashAmount"}, {"挂账金额", "creditAmount"},
                    {"收款金额", "paymentAmount"}, {"新增欠款", "newDebt"},
                    {"采购单数", "purchaseCount"}, {"采购金额", "purchaseAmount"}
            };
            for (String[] m : metrics) {
                Row r = s1.createRow(++i);
                r.createCell(0).setCellValue(m[0]);
                Object v = summary.get(m[1]);
                r.createCell(1).setCellValue(v == null ? "" : String.valueOf(v));
            }

            Sheet s2 = workbook.createSheet("按日明细");
            String[] dayHead = {"日期", "销售额", "现结", "挂账", "开单数"};
            writeHead(s2, headStyle, dayHead);
            int j = 0;
            for (Map<String, Object> d : byDay) {
                Row r = s2.createRow(++j);
                r.createCell(0).setCellValue(str(d.get("date")));
                r.createCell(1).setCellValue(num(d.get("amount")));
                r.createCell(2).setCellValue(num(d.get("cashAmount")));
                r.createCell(3).setCellValue(num(d.get("creditAmount")));
                r.createCell(4).setCellValue(str(d.get("count")));
            }

            Sheet s3 = workbook.createSheet("商品销量Top");
            String[] topHead = {"商品", "规格", "销量", "销售额", "毛利"};
            writeHead(s3, headStyle, topHead);
            int k = 0;
            for (Map<String, Object> t : top) {
                Row r = s3.createRow(++k);
                r.createCell(0).setCellValue(str(t.get("productName")));
                r.createCell(1).setCellValue(str(t.get("spec")));
                r.createCell(2).setCellValue(num(t.get("qty")));
                r.createCell(3).setCellValue(num(t.get("amount")));
                r.createCell(4).setCellValue(num(t.get("profit")));
            }

            for (Sheet s : new Sheet[]{s1, s2, s3}) {
                for (int c = 0; c < 6; c++) {
                    s.autoSizeColumn(c);
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    // ---------------- 私有工具 ----------------

    private Map<Long, List<SaleOrderItem>> itemsOf(List<SaleOrder> orders) {
        Map<Long, List<SaleOrderItem>> map = new LinkedHashMap<>();
        if (orders.isEmpty()) {
            return map;
        }
        Set<Long> ids = new HashSet<>();
        orders.forEach(o -> ids.add(o.getId()));
        for (SaleOrderItem it : saleItemMapper.selectList(new LambdaQueryWrapper<SaleOrderItem>()
                .in(SaleOrderItem::getSaleId, ids))) {
            map.computeIfAbsent(it.getSaleId(), k -> new ArrayList<>()).add(it);
        }
        return map;
    }

    private byte[] build(String sheetName, String[] headers, List<List<Object>> rows) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(sheetName);
            CellStyle headStyle = headStyle(workbook);
            writeHead(sheet, headStyle, headers);
            int rowIdx = 1;
            for (List<Object> r : rows) {
                Row row = sheet.createRow(rowIdx++);
                for (int c = 0; c < r.size(); c++) {
                    Object v = r.get(c);
                    Cell cell = row.createCell(c);
                    if (v instanceof BigDecimal b) {
                        cell.setCellValue(b.doubleValue());
                    } else if (v instanceof Number n) {
                        cell.setCellValue(n.doubleValue());
                    } else {
                        cell.setCellValue(v == null ? "" : String.valueOf(v));
                    }
                }
            }
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private CellStyle headStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private void writeHead(Sheet sheet, CellStyle style, String... headers) {
        Row head = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = head.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(style);
        }
    }

    private List<Object> row(Object... values) {
        return java.util.Arrays.asList(values);
    }

    private String date(LocalDate d) {
        return d == null ? "" : d.format(DATE);
    }

    private String payMethod(String v) {
        return "CASH".equals(v) ? "现结" : "CREDIT".equals(v) ? "挂账" : nvl(v);
    }

    private String status(String v) {
        return switch (v == null ? "" : v) {
            case "PAID" -> "已结清";
            case "UNPAID" -> "待收款";
            case "VOID" -> "已作废";
            default -> nvl(v);
        };
    }

    private double num(BigDecimal v) {
        return v == null ? 0 : v.setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private double num(Object v) {
        return v instanceof BigDecimal b ? num(b) : 0;
    }

    private String str(Object v) {
        if (v instanceof BigDecimal b) {
            return b.stripTrailingZeros().toPlainString();
        }
        return v == null ? "" : String.valueOf(v);
    }

    private String nvl(String v) {
        return v == null ? "" : v;
    }

    private BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
