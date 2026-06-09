package com.novablog.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 阿里云OSS配置类
 * 从 application.yml 中读取 aliyun.oss.* 配置项
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "aliyun.oss")
public class OssProperties {

    /**
     * OSS服务端点，如 oss-cn-beijing.aliyuncs.com
     */
    private String endpoint;

    /**
     * Bucket名称
     */
    private String bucket;

    /**
     * RAM用户AccessKey ID
     */
    private String accessKeyId;

    /**
     * RAM用户AccessKey Secret
     */
    private String accessKeySecret;
}
