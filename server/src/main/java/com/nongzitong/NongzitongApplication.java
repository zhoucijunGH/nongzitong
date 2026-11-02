package com.nongzitong;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 农资通启动类
 * <p>
 * 广西水果产地收购开单记账系统：开单、日结看板、欠款统计、收购流水导出。
 */
@MapperScan("com.nongzitong.mapper")
@SpringBootApplication
public class NongzitongApplication {

    public static void main(String[] args) {
        SpringApplication.run(NongzitongApplication.class, args);
    }
}
