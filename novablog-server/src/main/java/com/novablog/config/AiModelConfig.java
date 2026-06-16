package com.novablog.config;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingModel;
import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingOptions;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * AI 模型 Bean 配置
 * 根据 ai.chat.provider / ai.embedding.provider 动态装配 Spring AI 的 ChatModel / EmbeddingModel
 */
@Configuration
public class AiModelConfig {

    /**
     * DeepSeek 对话模型（OpenAI 兼容协议）
     */
    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "ai.chat", name = "provider", havingValue = "deepseek")
    public ChatModel deepseekChatModel(AiProperties aiProperties) {
        AiProperties.ProviderConfig config = aiProperties.getChat().getDeepseek();
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(config.getBaseUrl())
                .apiKey(config.getApiKey())
                .build();
        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(config.getModel())
                        .temperature(config.getTemperature())
                        .maxTokens(config.getMaxTokens())
                        .build())
                .build();
    }

    /**
     * OpenAI 对话模型
     */
    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "ai.chat", name = "provider", havingValue = "openai")
    public ChatModel openAiChatModel(AiProperties aiProperties) {
        AiProperties.ProviderConfig config = aiProperties.getChat().getOpenai();
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(config.getBaseUrl())
                .apiKey(config.getApiKey())
                .build();
        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(config.getModel())
                        .temperature(config.getTemperature())
                        .maxTokens(config.getMaxTokens())
                        .build())
                .build();
    }

    /**
     * 百炼 / 通义对话模型
     */
    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "ai.chat", name = "provider", havingValue = "bailian")
    public ChatModel bailianChatModel(AiProperties aiProperties) {
        AiProperties.ProviderConfig config = aiProperties.getChat().getBailian();
        DashScopeApi dashScopeApi = DashScopeApi.builder()
                .apiKey(config.getApiKey())
                .build();
        return DashScopeChatModel.builder()
                .dashScopeApi(dashScopeApi)
                .defaultOptions(DashScopeChatOptions.builder()
                        .withModel(config.getModel())
                        .withTemperature(config.getTemperature())
                        .withMaxToken(config.getMaxTokens())
                        .build())
                .build();
    }

    /**
     * DeepSeek 嵌入模型（OpenAI 兼容协议）
     */
    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "ai.embedding", name = "provider", havingValue = "deepseek")
    public EmbeddingModel deepseekEmbeddingModel(AiProperties aiProperties) {
        AiProperties.ProviderConfig config = aiProperties.getEmbedding().getDeepseek();
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(config.getBaseUrl())
                .apiKey(config.getApiKey())
                .build();
        return new OpenAiEmbeddingModel(openAiApi, MetadataMode.EMBED,
                OpenAiEmbeddingOptions.builder()
                        .model(config.getModel())
                        .build());
    }

    /**
     * OpenAI 嵌入模型
     */
    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "ai.embedding", name = "provider", havingValue = "openai")
    public EmbeddingModel openAiEmbeddingModel(AiProperties aiProperties) {
        AiProperties.ProviderConfig config = aiProperties.getEmbedding().getOpenai();
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(config.getBaseUrl())
                .apiKey(config.getApiKey())
                .build();
        return new OpenAiEmbeddingModel(openAiApi, MetadataMode.EMBED,
                OpenAiEmbeddingOptions.builder()
                        .model(config.getModel())
                        .build());
    }

    /**
     * 百炼 / 通义嵌入模型
     */
    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "ai.embedding", name = "provider", havingValue = "bailian")
    public EmbeddingModel bailianEmbeddingModel(AiProperties aiProperties) {
        AiProperties.ProviderConfig config = aiProperties.getEmbedding().getBailian();
        DashScopeApi dashScopeApi = DashScopeApi.builder()
                .apiKey(config.getApiKey())
                .build();
        return new DashScopeEmbeddingModel(dashScopeApi, MetadataMode.EMBED,
                DashScopeEmbeddingOptions.builder()
                        .withModel(config.getModel())
                        .build());
    }
}
