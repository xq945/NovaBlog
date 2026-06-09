package com.novablog.controller;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ObjectMetadata;
import com.novablog.common.Result;
import com.novablog.config.OssProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 文件上传控制器
 * 处理图片文件上传并存储到阿里云OSS
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/upload")
public class UploadController {

    private final OssProperties ossProperties;

    /**
     * 允许上传的MIME类型白名单
     */
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp"
    );

    /**
     * 允许的文件扩展名白名单
     */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");

    /**
     * 最大文件大小：5MB
     */
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;

    /**
     * 日期格式化器，用于构建存储路径
     */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * 通用文件上传接口
     *
     * @param file 上传的文件
     * @return 文件的公网访问URL
     */
    @PostMapping
    public Result<Map<String, Object>> upload(MultipartFile file) {
        // 1. 校验文件是否为空
        if (file == null || file.isEmpty()) {
            return Result.error(400, "请选择要上传的文件");
        }

        // 2. 校验文件大小
        if (file.getSize() > MAX_FILE_SIZE) {
            return Result.error(400, "文件大小不能超过5MB");
        }

        // 3. 校验MIME类型
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            return Result.error(400, "不支持的文件类型，仅允许jpg/png/gif/webp");
        }

        // 4. 校验文件扩展名
        String originalFilename = file.getOriginalFilename();
        String extension = getExtension(originalFilename);
        if (extension == null || !ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
            return Result.error(400, "不支持的文件格式");
        }

        // 5. 生成唯一文件名和存储路径
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String newFilename = uuid + "." + extension.toLowerCase();
        String dateDir = LocalDate.now().format(DATE_FORMATTER);
        String objectKey = "uploads/" + dateDir + "/" + newFilename;

        // 6. 上传到OSS
        OSS ossClient = null;
        try {
            ossClient = new OSSClientBuilder().build(
                    ossProperties.getEndpoint(),
                    ossProperties.getAccessKeyId(),
                    ossProperties.getAccessKeySecret()
            );

            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType(contentType);
            metadata.setContentLength(file.getSize());

            ossClient.putObject(ossProperties.getBucket(), objectKey, file.getInputStream(), metadata);

            // 7. 构建访问URL
            String url = "https://" + ossProperties.getBucket() + "." + ossProperties.getEndpoint() + "/" + objectKey;

            log.info("文件上传成功: filename={}, size={}, url={}", originalFilename, file.getSize(), url);

            return Result.success(Map.of(
                    "url", url,
                    "filename", newFilename,
                    "size", file.getSize()
            ));

        } catch (IOException e) {
            log.error("文件读取失败", e);
            return Result.error(500, "文件读取失败，请稍后重试");
        } catch (Exception e) {
            log.error("OSS上传失败", e);
            return Result.error(500, "文件上传失败，请稍后重试");
        } finally {
            if (ossClient != null) {
                ossClient.shutdown();
            }
        }
    }

    /**
     * 从文件名中提取扩展名
     *
     * @param filename 原始文件名
     * @return 扩展名（不含点号），无扩展名则返回null
     */
    private String getExtension(String filename) {
        if (filename == null || filename.isBlank()) {
            return null;
        }
        // 去除路径信息，防止路径遍历
        String name = filename;
        int lastSlash = name.lastIndexOf('/');
        if (lastSlash == -1) {
            lastSlash = name.lastIndexOf('\\');
        }
        if (lastSlash != -1) {
            name = name.substring(lastSlash + 1);
        }
        int lastDot = name.lastIndexOf('.');
        if (lastDot == -1 || lastDot == name.length() - 1) {
            return null;
        }
        return name.substring(lastDot + 1);
    }
}
