package com.novablog.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI / RAG 配置绑定类
 * 支持对话模型与嵌入模型使用不同提供商
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    /**
     * 是否启用 AI 功能
     */
    private boolean enabled = true;

    /**
     * 对话模型配置
     */
    private ModelConfig chat = new ModelConfig();

    /**
     * 嵌入模型配置
     */
    private ModelConfig embedding = new ModelConfig();

    /**
     * RAG 检索与生成参数
     */
    private RagConfig rag = new RagConfig();

    /**
     * 模型配置（对话 / 嵌入共用结构）
     */
    @Data
    public static class ModelConfig {

        /**
         * 当前使用的模型提供商：deepseek / openai / bailian
         */
        private String provider = "deepseek";

        /**
         * DeepSeek 配置
         */
        private ProviderConfig deepseek = new ProviderConfig();

        /**
         * OpenAI 配置
         */
        private ProviderConfig openai = new ProviderConfig();

        /**
         * 阿里云百炼（DashScope）配置
         */
        private ProviderConfig bailian = new ProviderConfig();

        /**
         * 根据当前 provider 返回对应配置
         */
        public ProviderConfig currentProvider() {
            return switch (provider.toLowerCase()) {
                case "openai" -> openai;
                case "bailian" -> bailian;
                default -> deepseek;
            };
        }
    }

    @Data
    public static class ProviderConfig {

        /**
         * OpenAI 兼容接口的 base URL
         */
        private String baseUrl;

        /**
         * API Key
         */
        private String apiKey;

        /**
         * 模型名称
         */
        private String model;

        /**
         * 温度：0-2，越低越确定（仅对话模型有效）
         */
        private double temperature = 0.7;

        /**
         * 单次回复最大 token 数（仅对话模型有效）
         */
        private int maxTokens = 1024;
    }

    @Data
    public static class RagConfig {

        /**
         * 检索 top-K 片段
         */
        private int topK = 5;

        /**
         * 分块目标 token 数
         */
        private int chunkSize = 500;

        /**
         * 分块重叠 token 数
         */
        private int chunkOverlap = 100;

        /**
         * 传给 LLM 的最大上下文 token 数
         */
        private int maxContextTokens = 3000;

        /**
         * 公开问答接口每 IP 每分钟限流次数
         */
        private int rateLimitPerMinute = 10;
    }
}
