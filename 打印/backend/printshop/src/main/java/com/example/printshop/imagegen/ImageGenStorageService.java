package com.example.printshop.imagegen;

import com.example.printshop.common.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

/**
 * AI 生成图存储：data/files 共享卷下的 imagegen/{accountId}/{token}.png
 * 目录解析链路镜像 FileInfoController.resolveUploadDir，保证与订单文件同卷。
 */
@Service
public class ImageGenStorageService {
    private static final Logger log = LoggerFactory.getLogger(ImageGenStorageService.class);
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[0-9a-fA-F-]{36}");

    private final String configuredDir;
    private final String uploadDirProperty;

    @Autowired
    public ImageGenStorageService(@Value("${printshop.imagegen.storage-dir:}") String configuredDir,
                                  @Value("${printshop.file.upload-dir:}") String uploadDirProperty) {
        this.configuredDir = configuredDir;
        this.uploadDirProperty = uploadDirProperty;
    }

    /** 落盘并返回对外相对路径（/files/imagegen/...），写入失败抛 503 */
    public String save(Integer accountId, String token, byte[] bytes) {
        Path base = resolveBaseDir();
        Path dir = base.resolve(String.valueOf(accountId));
        Path dest = dir.resolve(token + ".png").normalize();
        if (!dest.startsWith(base)) {
            throw ApiException.badRequest("invalid file path");
        }
        try {
            Files.createDirectories(dir);
            Files.write(dest, bytes);
        } catch (IOException exception) {
            log.error("imagegen save failed accountId={} token={}", accountId, token, exception);
            throw ApiException.serviceUnavailable("生成结果保存失败，请稍后重试");
        }
        return "/files/imagegen/" + accountId + "/" + token + ".png";
    }

    /** 下载解析：token 合法性 + 路径归一化 + 存在性，防目录穿越 */
    public Path resolve(Integer ownerId, String token) {
        if (ownerId == null || token == null || !TOKEN_PATTERN.matcher(token).matches()) {
            throw ApiException.notFound("文件不存在或已删除");
        }
        Path base = resolveBaseDir();
        Path dest = base.resolve(String.valueOf(ownerId)).resolve(token + ".png").normalize();
        if (!dest.startsWith(base) || !Files.exists(dest)) {
            throw ApiException.notFound("文件不存在或已删除");
        }
        return dest;
    }

    public void delete(Integer accountId, String token) {
        if (accountId == null || token == null || !TOKEN_PATTERN.matcher(token).matches()) {
            return;
        }
        try {
            Files.deleteIfExists(resolveBaseDir().resolve(String.valueOf(accountId)).resolve(token + ".png"));
        } catch (IOException exception) {
            log.warn("imagegen delete failed accountId={} token={}", accountId, token, exception);
        }
    }

    private Path resolveBaseDir() {
        if (configuredDir != null && !configuredDir.isBlank()) {
            return Paths.get(configuredDir).toAbsolutePath().normalize();
        }
        String parent = uploadDirProperty;
        if (parent == null || parent.isBlank()) {
            String productionDir = "/www/wwwroot/printshop/files/";
            if (new File(productionDir).exists()) {
                parent = productionDir;
            } else {
                parent = Paths.get(System.getProperty("user.dir"), "files").toString();
            }
        }
        return Paths.get(parent, "imagegen").toAbsolutePath().normalize();
    }
}
