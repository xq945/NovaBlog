package com.novablog.rag.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novablog.common.Result;
import com.novablog.common.annotation.RequireAdmin;
import com.novablog.rag.dto.AskRequest;
import com.novablog.rag.dto.AskResponse;
import com.novablog.rag.dto.IndexStatusVO;
import com.novablog.rag.service.ArticleIndexService;
import com.novablog.rag.service.RagQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * RAG 问答控制器
 */
@RestController
@RequestMapping("/rag")
@RequiredArgsConstructor
@Validated
@ConditionalOnProperty(prefix = "ai", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RagController {

    private final RagQueryService ragQueryService;
    private final ArticleIndexService articleIndexService;
    private final ObjectMapper objectMapper;

    /**
     * 向博客知识库提问（同步）
     *
     * @param request 问题
     * @return 回答与来源
     */
    @PostMapping("/ask")
    public Result<AskResponse> ask(@Valid @RequestBody AskRequest request) {
        AskResponse response = ragQueryService.ask(request.getQuestion().trim());
        return Result.success(response);
    }

    /**
     * 向博客知识库提问（流式 SSE）
     *
     * @param question 问题
     * @return SSE 流，最后一条事件 event=sources 携带来源文章 JSON
     */
    @GetMapping(value = "/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> askStream(@RequestParam String question) {
        RagQueryService.StreamResult result = ragQueryService.askStreamWithSources(question.trim());

        Flux<ServerSentEvent<String>> contentStream = result.content()
                .map(content -> ServerSentEvent.<String>builder()
                        .data(content)
                        .build());

        Mono<ServerSentEvent<String>> sourcesEvent = Mono.fromCallable(() -> {
            String json = objectMapper.writeValueAsString(Result.success(result.sources()));
            return ServerSentEvent.<String>builder()
                    .event("sources")
                    .data(json)
                    .build();
        }).onErrorResume(JsonProcessingException.class, e -> {
            String errorJson = "{\"code\":500,\"message\":\"来源序列化失败\"}";
            return Mono.just(ServerSentEvent.<String>builder()
                    .event("sources")
                    .data(errorJson)
                    .build());
        });

        return contentStream.concatWith(sourcesEvent);
    }

    /**
     * 手动重建所有已发布文章的索引（管理员）
     *
     * @return 成功结果
     */
    @PostMapping("/reindex")
    @RequireAdmin
    public Result<Void> reindex() {
        articleIndexService.rebuildAll();
        return Result.success();
    }

    /**
     * 查询索引状态（管理员）
     *
     * @return 索引状态统计
     */
    @GetMapping("/status")
    @RequireAdmin
    public Result<IndexStatusVO> status() {
        IndexStatusVO status = articleIndexService.getIndexStatus();
        return Result.success(status);
    }
}
