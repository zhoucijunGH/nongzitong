package com.nongzitong.controller;

import com.nongzitong.common.PageResult;
import com.nongzitong.common.Result;
import com.nongzitong.entity.Product;
import com.nongzitong.entity.StockFlow;
import com.nongzitong.service.StockService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 库存接口：当前库存表 + 库存流水（店员与老板均可查）。
 */
@RestController
@RequestMapping("/api/stocks")
public class StockController {

    private final StockService stockService;

    public StockController(StockService stockService) {
        this.stockService = stockService;
    }

    /** 当前库存（lowOnly=true 只看低库存） */
    @GetMapping
    public Result<List<Product>> stockList(@RequestParam(required = false) String keyword,
                                           @RequestParam(required = false) String category,
                                           @RequestParam(required = false) Boolean lowOnly) {
        return Result.ok(stockService.stockList(keyword, category, lowOnly));
    }

    /** 库存流水（只增不改） */
    @GetMapping("/flows")
    public Result<PageResult<StockFlow>> flows(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String flowType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(stockService.flowPage(keyword, flowType, dateFrom, dateTo, page, size));
    }
}
