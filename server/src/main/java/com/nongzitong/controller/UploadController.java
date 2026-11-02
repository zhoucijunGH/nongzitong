package com.nongzitong.controller;

import com.nongzitong.common.BizException;
import com.nongzitong.common.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 上传接口：磅单照片等，存到运行目录 ./uploads/。
 */
@RestController
@RequestMapping("/api/upload")
public class UploadController {

    /** 上传文件物理目录（相对运行目录） */
    @Value("${nongzitong.upload-dir:uploads}")
    private String uploadDir;

    /** multipart 字段名 file，返回 {url:"/uploads/xxx.jpg"} */
    @PostMapping
    public Result<Map<String, String>> upload(@RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BizException(400, "请选择要上传的文件");
        }
        String ext = resolveExt(file.getOriginalFilename());
        // 文件名：时间戳 + 随机数 + 扩展名，避免重名覆盖
        String filename = System.currentTimeMillis()
                + "_" + ThreadLocalRandom.current().nextInt(100000, 999999) + ext;

        Path dir = Paths.get(uploadDir);
        Files.createDirectories(dir);
        file.transferTo(dir.resolve(filename).toAbsolutePath());

        Map<String, String> data = new LinkedHashMap<>();
        data.put("url", "/uploads/" + filename);
        return Result.ok(data);
    }

    /** 提取安全的扩展名（字母数字，最长 8 位），默认 .bin */
    private String resolveExt(String original) {
        if (original != null && original.contains(".")) {
            String ext = original.substring(original.lastIndexOf('.') + 1).trim();
            if (ext.matches("[a-zA-Z0-9]{1,8}")) {
                return "." + ext.toLowerCase();
            }
        }
        return ".bin";
    }
}
