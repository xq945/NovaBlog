package com.novablog.rag.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown 处理工具
 * 用于 RAG 分块前的文本清洗
 */
public final class MarkdownUtils {

    private MarkdownUtils() {
    }

    /**
     * 代码块正则：匹配 ```...```
     */
    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("```[\\s\\S]*?```");

    /**
     * 行内代码正则：匹配 `...`
     */
    private static final Pattern INLINE_CODE_PATTERN = Pattern.compile("`([^`]+)`");

    /**
     * 图片正则：匹配 ![alt](url)
     */
    private static final Pattern IMAGE_PATTERN = Pattern.compile("!\\[.*?\\]\\(.*?\\)");

    /**
     * 链接正则：匹配 [text](url)
     */
    private static final Pattern LINK_PATTERN = Pattern.compile("\\[([^\\]]+)\\]\\(.*?\\)");

    /**
     * HTML 标签正则
     */
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");

    /**
     * 标题正则：匹配 # ## ### 等
     */
    private static final Pattern HEADING_PATTERN = Pattern.compile("^#{1,6}\\s*(.+)$", Pattern.MULTILINE);

    /**
     * 将 Markdown 转换为纯文本
     *
     * @param markdown Markdown 内容
     * @return 纯文本
     */
    public static String toPlainText(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return "";
        }
        String text = markdown;
        text = CODE_BLOCK_PATTERN.matcher(text).replaceAll(" ");
        text = INLINE_CODE_PATTERN.matcher(text).replaceAll("$1");
        text = IMAGE_PATTERN.matcher(text).replaceAll(" ");
        text = LINK_PATTERN.matcher(text).replaceAll("$1");
        text = HTML_TAG_PATTERN.matcher(text).replaceAll(" ");
        text = HEADING_PATTERN.matcher(text).replaceAll("$1");
        text = text.replaceAll("\\*+|_+|~+|`+", " ");
        text = text.replaceAll("\\n+", " ");
        return text.replaceAll("\\s+", " ").trim();
    }

    /**
     * 根据 Markdown 标题提取段落边界
     *
     * @param markdown Markdown 内容
     * @return 每个元素为一个段落（可能含标题行）
     */
    public static List<String> splitByHeadings(String markdown) {
        List<String> sections = new ArrayList<>();
        if (markdown == null || markdown.isEmpty()) {
            return sections;
        }

        String[] lines = markdown.split("\\r?\\n");
        StringBuilder current = new StringBuilder();

        for (String line : lines) {
            if (line.matches("^#{1,6}\\s+.+$")) {
                if (current.length() > 0) {
                    sections.add(current.toString().trim());
                    current.setLength(0);
                }
            }
            current.append(line).append("\n");
        }

        if (current.length() > 0) {
            sections.add(current.toString().trim());
        }

        return sections;
    }

    /**
     * 估算文本 token 数
     * 中文按 2 字符/token，英文按 4 字符/token 的混合启发式
     *
     * @param text 文本
     * @return token 估算数
     */
    public static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int chineseChars = 0;
        int otherChars = 0;
        for (char c : text.toCharArray()) {
            if (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                    || Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                    || Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS) {
                chineseChars++;
            } else if (!Character.isWhitespace(c)) {
                otherChars++;
            }
        }
        return (int) Math.ceil(chineseChars / 2.0 + otherChars / 4.0);
    }

    /**
     * 按目标 token 数和重叠 token 数切分长文本
     *
     * @param text       待切分文本
     * @param chunkSize  目标 token 数
     * @param overlap    重叠 token 数
     * @return 切分后的文本列表
     */
    public static List<String> splitByTokens(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return chunks;
        }

        if (estimateTokens(text) <= chunkSize) {
            chunks.add(text);
            return chunks;
        }

        // 按句子切分（以中文句号、英文句号、问号、感叹号、分号分隔）
        String[] sentences = text.split("(?<=[。！？.?!;；])");
        StringBuilder current = new StringBuilder();
        int currentTokens = 0;

        for (String sentence : sentences) {
            String trimmed = sentence.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int sentenceTokens = estimateTokens(trimmed);

            if (currentTokens + sentenceTokens > chunkSize && current.length() > 0) {
                chunks.add(current.toString().trim());

                // 重叠：从当前 chunk 末尾向前取约 overlap token 的内容
                String previous = current.toString();
                current.setLength(0);
                currentTokens = 0;

                if (overlap > 0) {
                    String overlapText = takeTokensFromEnd(previous, overlap);
                    current.append(overlapText).append(" ");
                    currentTokens = estimateTokens(overlapText);
                }
            }

            current.append(trimmed).append(" ");
            currentTokens += sentenceTokens;
        }

        if (current.length() > 0) {
            chunks.add(current.toString().trim());
        }

        return chunks;
    }

    /**
     * 从文本末尾截取约 targetTokens 个 token 的内容
     */
    private static String takeTokensFromEnd(String text, int targetTokens) {
        String[] sentences = text.split("(?<=[。！？.?!;；])");
        StringBuilder result = new StringBuilder();
        int tokens = 0;
        for (int i = sentences.length - 1; i >= 0; i--) {
            String sentence = sentences[i].trim();
            if (sentence.isEmpty()) {
                continue;
            }
            int sentenceTokens = estimateTokens(sentence);
            if (tokens + sentenceTokens > targetTokens && tokens > 0) {
                break;
            }
            result.insert(0, sentence);
            tokens += sentenceTokens;
        }
        return result.toString().trim();
    }
}
