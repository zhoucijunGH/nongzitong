package com.nongzitong.controller;

import com.nongzitong.common.Result;
import com.nongzitong.service.ExportService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 导出接口：销售/采购/库存/欠款/经营报表 → xlsx，仅老板（ADMIN）。
 */
@RestController
@RequestMapping("/api/export")
@PreAuthorize("hasRole('ADMIN')")
public class ExportController {

    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final ExportService exportService;

    public ExportController(ExportService exportService) {
        this.exportService = exportService;
    }

    @GetMapping("/sales")
    public void sales(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
                      HttpServletResponse response) throws IOException {
        write(response, "销售流水", exportService.exportSales(dateFrom, dateTo));
    }

    @GetMapping("/purchases")
    public void purchases(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
                          HttpServletResponse response) throws IOException {
        write(response, "采购入库", exportService.exportPurchases(dateFrom, dateTo));
    }

    @GetMapping("/stock")
    public void stock(HttpServletResponse response) throws IOException {
        write(response, "当前库存", exportService.exportStock());
    }

    @GetMapping("/debts")
    public void debts(@RequestParam(required = false) String keyword,
                      HttpServletResponse response) throws IOException {
        write(response, "客户欠款", exportService.exportDebts(keyword));
    }

    @GetMapping("/report")
    public void report(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
                       HttpServletResponse response) throws IOException {
        write(response, "经营报表", exportService.exportReport(dateFrom, dateTo));
    }

    private void write(HttpServletResponse response, String name, byte[] bytes) throws IOException {
        String filename = URLEncoder.encode(name + "-" + LocalDate.now().format(FILE_DATE) + ".xlsx",
                StandardCharsets.UTF_8);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=" + filename);
        response.getOutputStream().write(bytes);
    }
}
