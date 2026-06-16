package com.novablog.chat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * AI 对话标题生成服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatTitleService {

    private final ChatModel chatModel;

    /**
     * 标题最大长度
     */
    private static final int MAX_TITLE_LENGTH = 30;

    /**
     * 根据用户问题生成会话标题
     *
     * @param question 用户问题
     * @return 标题
     */
    public String generateTitle(String question) {
        if (!StringUtils.hasText(question)) {
            return "新对话";
        }

        try {
            ChatClient chatClient = ChatClient.create(chatModel);
            String title = chatClient.prompt()
                    .system("你是一个对话标题生成助手。请用不超过10个字总结用户的问题作为对话标题，不要加引号，不要解释。")
                    .user(question.trim())
                    .call()
                    .content();

            return cleanTitle(title, question);
        } catch (Exception e) {
            log.warn("AI 生成标题失败，使用兜底标题", e);
            return fallbackTitle(question);
        }
    }

    /**
     * 清洗模型返回的标题
     */
    private String cleanTitle(String title, String originalQuestion) {
        if (!StringUtils.hasText(title)) {
            return fallbackTitle(originalQuestion);
        }

        String cleaned = title.trim();
        // 去除首尾可能存在的引号
        cleaned = cleaned.replaceAll("^[\"'\"'\"'\"]+|[\"'\"'\"'\"]+$", "");
        // 去除首尾空白
        cleaned = cleaned.strip();

        if (cleaned.length() > MAX_TITLE_LENGTH) {
            cleaned = cleaned.substring(0, MAX_TITLE_LENGTH);
        }
        return cleaned.isEmpty() ? fallbackTitle(originalQuestion) : cleaned;
    }

    /**
     * 兜底标题：截取问题前 30 字
     */
    private String fallbackTitle(String question) {
        String trimmed = question.trim();
        if (trimmed.length() <= MAX_TITLE_LENGTH) {
            return trimmed;
        }
        return trimmed.substring(0, MAX_TITLE_LENGTH) + "...";
    }
}
