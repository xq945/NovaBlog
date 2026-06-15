package com.novablog.config;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 阿里云 OSS 客户端配置
 * 将 OSSClient 注册为 Spring Bean，避免每次上传重复创建客户端，提升性能并确保连接复用
 */
@Configuration
public class OssClientConfig {

    /**
     * 创建单例 OSS 客户端
     * destroyMethod 指定为 shutdown，确保应用关闭时释放连接资源
     *
     * @param ossProperties OSS 配置属性
     * @return OSS 客户端实例
     */
    @Bean(destroyMethod = "shutdown")
    public OSS ossClient(OssProperties ossProperties) {
        return new OSSClientBuilder().build(
                ossProperties.getEndpoint(),
                ossProperties.getAccessKeyId(),
                ossProperties.getAccessKeySecret()
        );
    }
}
