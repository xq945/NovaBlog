package com.novablog.service;

import com.novablog.common.exception.BusinessException;
import com.novablog.config.AiProperties;
import com.novablog.dto.ArticleImportResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文件导入文章服务
 * 支持 .md、.txt、.docx、.pdf 格式，仅提取文本内容
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleImportService {

    /**
     * 最大允许文件大小：10MB
     */
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

    /**
     * Markdown 一级标题正则
     */
    private static final Pattern MARKDOWN_TITLE_PATTERN = Pattern.compile("^#\\s+(.+)$", Pattern.MULTILINE);

    /**
     * 摘要最大长度
     */
    private static final int SUMMARY_MAX_LENGTH = 150;

    private final AiSummaryService aiSummaryService;
    private final AiProperties aiProperties;

    /**
     * 导入文件并解析为文章数据
     *
     * @param file 上传的文件
     * @return 解析结果
     */
    public ArticleImportResult importFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(400, "文件大小不能超过 10MB");
        }

        String originalFilename = file.getOriginalFilename();
        String extension = getExtension(originalFilename);

        String content;
        try {
            content = switch (extension) {
                case "md", "txt" -> parseTextFile(file.getInputStream());
                case "docx" -> parseDocx(file.getInputStream());
                case "pdf" -> parsePdf(file.getInputStream());
                default -> throw new BusinessException(400, "不支持的文件格式，仅支持 .md、.txt、.docx、.pdf");
            };
        } catch (IOException e) {
            log.error("文件读取失败, filename={}", originalFilename, e);
            throw new BusinessException(500, "文件读取失败");
        }

        if (content == null || content.isBlank()) {
            throw new BusinessException(400, "未能从文件中提取到有效文本");
        }

        ArticleImportResult result = new ArticleImportResult();
        result.setTitle(extractTitle(content, originalFilename, extension));
        result.setContent(content.trim());
        result.setSummary(extractSummary(content));
        return result;
    }

    /**
     * 解析纯文本文件（Markdown / TXT）
     */
    private String parseTextFile(InputStream inputStream) throws IOException {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }
        return content.toString();
    }

    /**
     * 解析 Word .docx 文件
     */
    private String parseDocx(InputStream inputStream) throws IOException {
        StringBuilder content = new StringBuilder();
        try (XWPFDocument document = new XWPFDocument(inputStream)) {
            List<XWPFParagraph> paragraphs = document.getParagraphs();
            for (XWPFParagraph paragraph : paragraphs) {
                String text = paragraph.getText();
                if (text != null && !text.isBlank()) {
                    content.append(text).append("\n\n");
                }
            }
        }
        return content.toString();
    }

    /**
     * 解析 PDF 文件
     */
    private String parsePdf(InputStream inputStream) throws IOException {
        try (PDDocument document = Loader.loadPDF(inputStream.readAllBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    /**
     * 提取标题
     */
    private String extractTitle(String content, String filename, String extension) {
        // Markdown 优先取第一个一级标题
        if ("md".equals(extension)) {
            Matcher matcher = MARKDOWN_TITLE_PATTERN.matcher(content);
            if (matcher.find()) {
                String title = matcher.group(1).trim();
                if (!title.isEmpty()) {
                    return title;
                }
            }
        }

        // 其他格式使用文件名作为标题
        if (filename != null && !filename.isBlank()) {
            int dotIndex = filename.lastIndexOf('.');
            String title = dotIndex > 0 ? filename.substring(0, dotIndex) : filename;
            return title.trim();
        }

        return "未命名文章";
    }

    /**
     * 提取兜底摘要（正文前 150 字符）
     */
    private String extractFallbackSummary(String content) {
        String plainText = content
                .replaceAll("```[\\s\\S]*?```", "")
                .replaceAll("`([^`]+)`", "$1")
                .replaceAll("!\\[.*?\\]\\(.*?\\)", "")
                .replaceAll("\\[([^\\]]+)\\]\\(.*?\\)", "$1")
                .replaceAll("#{1,6}\\s*", "")
                .replaceAll("\\*+|_+|~+|`+", "")
                .replaceAll("\\n+", " ")
                .trim();

        if (plainText.length() <= SUMMARY_MAX_LENGTH) {
            return plainText;
        }
        return plainText.substring(0, SUMMARY_MAX_LENGTH);
    }

    /**
     * 提取摘要：优先使用 AI 生成，失败时降级为前 150 字符截断
     */
    private String extractSummary(String content) {
        if (!aiProperties.isEnabled()) {
            return extractFallbackSummary(content);
        }

        try {
            String summary = aiSummaryService.generateSummary(content);
            if (summary != null && !summary.isBlank()) {
                return summary;
            }
        } catch (BusinessException e) {
            log.warn("AI 摘要生成失败，使用兜底摘要。原因：{}", e.getMessage());
        } catch (Exception e) {
            log.error("AI 摘要生成异常，使用兜底摘要", e);
        }

        return extractFallbackSummary(content);
    }

    /**
     * 获取文件扩展名（小写）
     */
    private String getExtension(String filename) {
        if (filename == null || filename.lastIndexOf('.') == -1) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }
}
