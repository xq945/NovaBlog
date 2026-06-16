package com.novablog.service;

import com.novablog.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

/**
 * AI 摘要生成服务
 * 基于大模型根据文章正文生成摘要
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiSummaryService {

    private final ChatModel chatModel;

    /**
     * 正文最小长度
     */
    private static final int MIN_CONTENT_LENGTH = 100;

    /**
     * 正文最大长度
     */
    private static final int MAX_CONTENT_LENGTH = 50000;

    /**
     * 摘要最大长度
     */
    private static final int MAX_SUMMARY_LENGTH = 500;

    /**
     * 系统提示词
     */
    private static final String SYSTEM_PROMPT = """
            你是一名技术博客编辑助手。请根据用户提供的文章正文生成一段中文摘要。
            要求：
            1. 摘要控制在 150 字以内。
            2. 准确概括文章核心观点和关键信息。
            3. 语言简洁、通顺，不使用"本文"、"作者"等套话。
            4. 只输出摘要内容，不要输出任何解释或额外内容。
            """;

    /**
     * 根据正文生成摘要
     *
     * @param content 文章正文
     * @return 生成的摘要
     */
    public String generateSummary(String content) {
        if (content == null || content.isBlank()) {
            throw new BusinessException(400, "正文不能为空");
        }
        if (content.length() < MIN_CONTENT_LENGTH) {
            throw new BusinessException(400, "正文内容过少，无法生成摘要");
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw new BusinessException(400, "正文过长，无法生成摘要");
        }

        try {
            String summary = ChatClient.create(chatModel)
                    .prompt()
                    .system(SYSTEM_PROMPT)
                    .user("正文：\n" + content)
                    .call()
                    .content();

            if (summary == null || summary.isBlank()) {
                throw new BusinessException(500, "摘要生成失败，请稍后重试");
            }

            summary = summary.trim();
            if (summary.length() > MAX_SUMMARY_LENGTH) {
                summary = summary.substring(0, MAX_SUMMARY_LENGTH);
            }
            return summary;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI 摘要生成失败", e);
            throw new BusinessException(500, "摘要生成失败，请稍后重试");
        }
    }
}
