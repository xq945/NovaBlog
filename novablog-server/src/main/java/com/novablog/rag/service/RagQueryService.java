package com.novablog.rag.service;

import com.novablog.chat.vo.ChatMessageVO;
import com.novablog.config.AiProperties;
import com.novablog.mapper.ArticleMapper;
import com.novablog.rag.dto.AskResponse;
import com.novablog.rag.dto.SourceArticle;
import com.novablog.rag.util.MarkdownUtils;
import com.novablog.vo.ArticleVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * RAG 查询服务
 * 负责检索相关文章片段并生成带引用的回答
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagQueryService {

    private final ChatModel chatModel;
    private final VectorStore vectorStore;
    private final ArticleMapper articleMapper;
    private final AiProperties aiProperties;

    /**
     * 引用标记正则：匹配 [1]、[12] 等
     */
    private static final Pattern CITATION_PATTERN = Pattern.compile("\\[(\\d+)\\]");

    /**
     * 最大允许的问题 token 数
     */
    private static final int MAX_QUESTION_TOKENS = 200;

    /**
     * 同步问答（保留兼容）
     */
    public AskResponse ask(String question) {
        return ask(question, List.of());
    }

    /**
     * 同步问答（带历史上下文）
     */
    public AskResponse ask(String question, List<ChatMessageVO> history) {
        if (question == null || question.trim().isEmpty()) {
            throw new IllegalArgumentException("问题不能为空");
        }
        if (MarkdownUtils.estimateTokens(question) > MAX_QUESTION_TOKENS) {
            throw new IllegalArgumentException("问题过长，请控制在 200 token 以内");
        }

        // 1. 通过 VectorStore 检索相关片段
        List<Document> documents = retrieveDocuments(question);
        if (documents.isEmpty()) {
            AskResponse response = new AskResponse();
            response.setAnswer("当前博客还没有建立知识库索引，请联系管理员先发布文章或重建索引。");
            response.setSourceArticles(List.of());
            return response;
        }

        // 2. 构建来源和上下文
        List<SourceArticle> sources = buildSources(documents);
        String context = buildContext(sources);

        // 3. 调用大模型生成回答
        String answer = generateAnswer(question, context, history);

        // 4. 过滤掉模型未引用的来源
        List<SourceArticle> citedSources = filterCitedSources(answer, sources);

        AskResponse response = new AskResponse();
        response.setAnswer(answer);
        response.setSourceArticles(citedSources);
        return response;
    }

    /**
     * 流式问答结果
     */
    public record StreamResult(Flux<String> content, List<SourceArticle> sources) {
    }

    /**
     * 流式问答（带来源）
     *
     * @param question 用户问题
     * @return 内容流与来源文章
     */
    public StreamResult askStreamWithSources(String question) {
        return askStreamWithSources(question, List.of());
    }

    /**
     * 流式问答（带来源 + 历史上下文）
     *
     * @param question 用户问题
     * @param history  历史消息上下文
     * @return 内容流与来源文章
     */
    public StreamResult askStreamWithSources(String question, List<ChatMessageVO> history) {
        if (question == null || question.trim().isEmpty()) {
            return new StreamResult(
                    Flux.error(new IllegalArgumentException("问题不能为空")),
                    List.of()
            );
        }
        if (MarkdownUtils.estimateTokens(question) > MAX_QUESTION_TOKENS) {
            return new StreamResult(
                    Flux.error(new IllegalArgumentException("问题过长，请控制在 200 token 以内")),
                    List.of()
            );
        }

        // 1. 通过 VectorStore 检索相关片段
        List<Document> documents = retrieveDocuments(question);
        if (documents.isEmpty()) {
            return new StreamResult(
                    Flux.just("当前博客还没有建立知识库索引，请联系管理员先发布文章或重建索引。"),
                    List.of()
            );
        }

        // 2. 构建来源和上下文
        List<SourceArticle> sources = buildSources(documents);
        String context = buildContext(sources);

        // 3. 流式调用大模型
        Flux<String> content = generateAnswerStream(question, context, history);
        return new StreamResult(content, sources);
    }

    /**
     * 检索相关文档
     */
    private List<Document> retrieveDocuments(String question) {
        SearchRequest request = SearchRequest.builder()
                .query(question.trim())
                .topK(aiProperties.getRag().getTopK())
                .similarityThreshold(0.3)
                .build();
        return vectorStore.similaritySearch(request);
    }

    /**
     * 根据检索结果构建来源文章列表
     */
    private List<SourceArticle> buildSources(List<Document> documents) {
        List<Long> articleIds = documents.stream()
                .map(doc -> {
                    Object articleId = doc.getMetadata().get("articleId");
                    if (articleId instanceof Number number) {
                        return number.longValue();
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, ArticleVO> articleMap = articleMapper.findByIds(articleIds).stream()
                .collect(Collectors.toMap(ArticleVO::getId, a -> a));

        List<SourceArticle> sources = new ArrayList<>();
        int index = 1;
        for (Document doc : documents) {
            Object articleIdObj = doc.getMetadata().get("articleId");
            if (!(articleIdObj instanceof Number number)) {
                continue;
            }
            Long articleId = number.longValue();
            ArticleVO article = articleMap.get(articleId);
            if (article == null) {
                continue;
            }

            Object scoreObj = doc.getMetadata().get("score");
            double score = scoreObj instanceof Number num ? num.doubleValue() : 0.0;

            SourceArticle source = new SourceArticle();
            source.setId(article.getId());
            source.setTitle(article.getTitle());
            source.setSummary(article.getSummary());
            source.setSnippet(doc.getText());
            source.setScore(score);
            sources.add(source);
            index++;
        }
        return sources;
    }

    /**
     * 构建传给 LLM 的上下文
     */
    private String buildContext(List<SourceArticle> sources) {
        StringBuilder context = new StringBuilder();
        int maxTokens = aiProperties.getRag().getMaxContextTokens();
        int currentTokens = 0;

        for (int i = 0; i < sources.size(); i++) {
            SourceArticle source = sources.get(i);
            String entry = "[" + (i + 1) + "] 标题：" + source.getTitle() + "\n"
                    + "内容：" + source.getSnippet() + "\n\n";
            int entryTokens = MarkdownUtils.estimateTokens(entry);
            if (currentTokens + entryTokens > maxTokens) {
                break;
            }
            context.append(entry);
            currentTokens += entryTokens;
        }

        return context.toString();
    }

    /**
     * 调用大模型生成回答
     */
    private String generateAnswer(String question, String context, List<ChatMessageVO> history) {
        String systemPrompt = buildSystemPrompt(context, history);
        ChatClient chatClient = ChatClient.create(chatModel);
        return chatClient.prompt()
                .system(systemPrompt)
                .user("问题：" + question)
                .call()
                .content();
    }

    /**
     * 流式调用大模型生成回答
     */
    private Flux<String> generateAnswerStream(String question, String context, List<ChatMessageVO> history) {
        String systemPrompt = buildSystemPrompt(context, history);
        ChatClient chatClient = ChatClient.create(chatModel);
        return chatClient.prompt()
                .system(systemPrompt)
                .user("问题：" + question)
                .stream()
                .content();
    }

    /**
     * 构造系统提示词
     */
    private String buildSystemPrompt(String context, List<ChatMessageVO> history) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一个基于博客内容的问答助手。请严格根据下面提供的参考文章片段回答问题。\n");
        prompt.append("规则：\n");
        prompt.append("1. 只使用参考内容中的信息作答，不要引入外部知识。\n");
        prompt.append("2. 如果参考内容不足以回答问题，请明确说明\"根据现有博客内容无法回答\"。\n");
        prompt.append("3. 回答中引用来源时使用 [1]、[2] 等标记，对应参考片段的编号。\n");
        prompt.append("4. 回答简洁、准确，优先使用中文。\n");

        if (history != null && !history.isEmpty()) {
            prompt.append("\n对话历史（请结合上下文理解最新问题）：\n");
            // 最多保留最近 6 条消息（3 轮问答），避免上下文过长
            int start = Math.max(0, history.size() - 6);
            for (int i = start; i < history.size(); i++) {
                ChatMessageVO msg = history.get(i);
                String roleName = "user".equals(msg.getRole()) ? "User" : "Assistant";
                prompt.append(roleName).append("：").append(msg.getContent()).append("\n");
            }
        }

        prompt.append("\n参考内容：\n").append(context);
        return prompt.toString();
    }

    /**
     * 根据回答中的引用标记过滤来源
     */
    private List<SourceArticle> filterCitedSources(String answer, List<SourceArticle> sources) {
        if (sources == null || sources.isEmpty()) {
            return List.of();
        }

        Matcher matcher = CITATION_PATTERN.matcher(answer);
        List<Integer> citedIndexes = new ArrayList<>();
        while (matcher.find()) {
            try {
                int idx = Integer.parseInt(matcher.group(1));
                if (idx >= 1 && idx <= sources.size()) {
                    citedIndexes.add(idx);
                }
            } catch (NumberFormatException ignored) {
            }
        }

        if (citedIndexes.isEmpty()) {
            return sources.subList(0, Math.min(3, sources.size()));
        }

        return citedIndexes.stream()
                .distinct()
                .map(idx -> sources.get(idx - 1))
                .collect(Collectors.toList());
    }
}
