package com.novablog.chat.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novablog.chat.dto.ChatAskRequest;
import com.novablog.chat.dto.CreateSessionRequest;
import com.novablog.chat.dto.EditMessageRequest;
import com.novablog.chat.dto.UpdateTitleRequest;
import com.novablog.chat.service.ChatSessionService;
import com.novablog.chat.vo.ChatMessageVO;
import com.novablog.chat.vo.ChatSessionDetailVO;
import com.novablog.chat.vo.ChatSessionVO;
import com.novablog.common.PageResult;
import com.novablog.common.Result;
import com.novablog.rag.dto.AskResponse;
import com.novablog.rag.service.RagQueryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * AI 对话控制器
 * 提供登录用户的会话管理与持久化问答能力
 */
@Slf4j
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
@Validated
public class ChatController {

    private final ChatSessionService sessionService;
    private final RagQueryService ragQueryService;
    private final ObjectMapper objectMapper;

    /**
     * 创建新会话
     *
     * @param request 创建请求
     * @return 会话信息
     */
    @PostMapping("/session")
    public Result<ChatSessionVO> createSession(@Valid @RequestBody CreateSessionRequest request) {
        ChatSessionVO session;
        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            session = sessionService.createEmptySession(request.getTitle());
        } else {
            session = sessionService.createSession(null);
        }
        return Result.success(session);
    }

    /**
     * 获取当前用户会话列表
     *
     * @param page 页码
     * @param size 每页数量
     * @return 分页结果
     */
    @GetMapping("/session/list")
    public Result<PageResult<ChatSessionVO>> listSessions(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) int size) {
        return Result.success(sessionService.listSessions(page, size));
    }

    /**
     * 获取会话详情（含消息）
     *
     * @param sessionId 会话ID
     * @return 会话详情
     */
    @GetMapping("/session/{sessionId}")
    public Result<ChatSessionDetailVO> getSessionDetail(@PathVariable Long sessionId) {
        return Result.success(sessionService.getSessionDetail(sessionId));
    }

    /**
     * 更新会话标题
     *
     * @param sessionId 会话ID
     * @param request   标题请求
     * @return 成功结果
     */
    @PutMapping("/session/{sessionId}/title")
    public Result<Void> updateTitle(
            @PathVariable Long sessionId,
            @Valid @RequestBody UpdateTitleRequest request) {
        sessionService.updateTitle(sessionId, request.getTitle());
        return Result.success();
    }

    /**
     * 删除会话
     *
     * @param sessionId 会话ID
     * @return 成功结果
     */
    @DeleteMapping("/session/{sessionId}")
    public Result<Void> deleteSession(@PathVariable Long sessionId) {
        sessionService.deleteSession(sessionId);
        return Result.success();
    }

    /**
     * 删除单条消息及其之后的消息
     *
     * @param sessionId 会话ID
     * @param messageId 消息ID
     * @return 成功结果
     */
    @DeleteMapping("/session/{sessionId}/message/{messageId}")
    public Result<Void> deleteMessage(
            @PathVariable Long sessionId,
            @PathVariable Long messageId) {
        sessionService.deleteMessage(sessionId, messageId);
        return Result.success();
    }

    /**
     * 同步问答（带会话记录）
     *
     * @param request 问答请求
     * @return 回答与来源
     */
    @PostMapping("/ask")
    public Result<AskResponse> ask(@Valid @RequestBody ChatAskRequest request) {
        String question = request.getQuestion().trim();
        Long sessionId = request.getSessionId();

        // 无 sessionId 时自动创建新会话
        if (sessionId == null) {
            ChatSessionVO session = sessionService.createSession(question);
            sessionId = session.getId();
        } else {
            sessionService.validateSessionOwnership(sessionId);
        }

        // 保存用户问题
        sessionService.saveUserMessage(sessionId, question);

        // 获取历史消息作为上下文（排除刚保存的当前问题）
        List<ChatMessageVO> history = excludeCurrentQuestion(
                sessionService.getSessionMessages(sessionId), question);

        // 调用 RAG 生成回答
        AskResponse response = ragQueryService.ask(question, history);

        // 保存 AI 回答
        saveAssistantResponse(sessionId, response);

        response.setSessionId(sessionId);
        return Result.success(response);
    }

    /**
     * 流式问答（带会话记录，SSE）
     *
     * @param sessionId 会话ID（可选）
     * @param question  用户问题
     * @return SSE 事件流
     */
    @GetMapping(value = "/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> askStream(
            @RequestParam(required = false) Long sessionId,
            @RequestParam String question) {

        String trimmedQuestion = question.trim();
        final Long finalSessionId = resolveSessionId(sessionId, trimmedQuestion);

        // 保存用户问题
        sessionService.saveUserMessage(finalSessionId, trimmedQuestion);

        // 获取历史消息作为上下文（排除刚保存的当前问题）
        List<ChatMessageVO> history = excludeCurrentQuestion(
                sessionService.getSessionMessages(finalSessionId), trimmedQuestion);

        // 调用 RAG 流式服务
        RagQueryService.StreamResult result = ragQueryService.askStreamWithSources(trimmedQuestion, history);

        StringBuilder answerBuilder = new StringBuilder();

        Flux<ServerSentEvent<String>> contentStream = result.content()
                .doOnNext(answerBuilder::append)
                .map(chunk -> ServerSentEvent.<String>builder()
                        .data(chunk)
                        .build());

        Mono<ServerSentEvent<String>> sourcesEvent = Mono.fromCallable(() -> {
            String json = objectMapper.writeValueAsString(Result.success(result.sources()));
            return ServerSentEvent.<String>builder()
                    .event("sources")
                    .data(json)
                    .build();
        }).onErrorResume(JsonProcessingException.class, e -> {
            log.error("来源序列化失败", e);
            String errorJson = "{\"code\":500,\"message\":\"来源序列化失败\"}";
            return Mono.just(ServerSentEvent.<String>builder()
                    .event("sources")
                    .data(errorJson)
                    .build());
        });

        // 流结束后保存 AI 回答
        Mono<ServerSentEvent<String>> saveEvent = Mono.fromRunnable(() -> {
            try {
                String sourcesJson = objectMapper.writeValueAsString(result.sources());
                sessionService.saveAssistantMessage(finalSessionId, answerBuilder.toString(), sourcesJson);
                sessionService.adjustMessageCount(finalSessionId);
            } catch (JsonProcessingException e) {
                log.error("AI 回答来源序列化失败", e);
                sessionService.saveAssistantMessage(finalSessionId, answerBuilder.toString(), "[]");
                sessionService.adjustMessageCount(finalSessionId);
            }
        }).then(Mono.empty());

        return contentStream.concatWith(sourcesEvent).concatWith(saveEvent);
    }

    /**
     * 编辑用户问题并重新生成回答（SSE 流式）
     *
     * @param sessionId 会话ID
     * @param messageId 消息ID
     * @param request   编辑请求
     * @return SSE 事件流
     */
    @PutMapping(value = "/session/{sessionId}/message/{messageId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> editAndRegenerate(
            @PathVariable Long sessionId,
            @PathVariable Long messageId,
            @Valid @RequestBody EditMessageRequest request) {

        String newContent = request.getContent().trim();

        // 编辑问题并获取新的历史上下文
        List<ChatMessageVO> history = sessionService.editMessage(sessionId, messageId, newContent);
        // 排除编辑后的当前问题本身
        history = excludeCurrentQuestion(history, newContent);

        // 重新生成回答
        RagQueryService.StreamResult result = ragQueryService.askStreamWithSources(newContent, history);

        StringBuilder answerBuilder = new StringBuilder();

        Flux<ServerSentEvent<String>> contentStream = result.content()
                .doOnNext(answerBuilder::append)
                .map(chunk -> ServerSentEvent.<String>builder()
                        .data(chunk)
                        .build());

        Mono<ServerSentEvent<String>> sourcesEvent = Mono.fromCallable(() -> {
            String json = objectMapper.writeValueAsString(Result.success(result.sources()));
            return ServerSentEvent.<String>builder()
                    .event("sources")
                    .data(json)
                    .build();
        }).onErrorResume(JsonProcessingException.class, e -> {
            log.error("来源序列化失败", e);
            String errorJson = "{\"code\":500,\"message\":\"来源序列化失败\"}";
            return Mono.just(ServerSentEvent.<String>builder()
                    .event("sources")
                    .data(errorJson)
                    .build());
        });

        // 流结束后保存新的 AI 回答
        Mono<ServerSentEvent<String>> saveEvent = Mono.fromRunnable(() -> {
            try {
                String sourcesJson = objectMapper.writeValueAsString(result.sources());
                sessionService.saveAssistantMessage(sessionId, answerBuilder.toString(), sourcesJson);
                sessionService.adjustMessageCount(sessionId);
            } catch (JsonProcessingException e) {
                log.error("AI 回答来源序列化失败", e);
                sessionService.saveAssistantMessage(sessionId, answerBuilder.toString(), "[]");
                sessionService.adjustMessageCount(sessionId);
            }
        }).then(Mono.empty());

        return contentStream.concatWith(sourcesEvent).concatWith(saveEvent);
    }

    /**
     * 解析或创建会话ID
     */
    private Long resolveSessionId(Long sessionId, String question) {
        if (sessionId == null) {
            ChatSessionVO session = sessionService.createSession(question);
            return session.getId();
        }
        sessionService.validateSessionOwnership(sessionId);
        return sessionId;
    }

    /**
     * 从历史消息中排除当前问题本身（最后一条 user 消息且内容与当前问题相同）
     */
    private List<ChatMessageVO> excludeCurrentQuestion(List<ChatMessageVO> history, String currentQuestion) {
        if (history == null || history.isEmpty()) {
            return history;
        }
        ChatMessageVO last = history.get(history.size() - 1);
        if ("user".equals(last.getRole()) && currentQuestion.equals(last.getContent())) {
            return history.subList(0, history.size() - 1);
        }
        return history;
    }

    /**
     * 保存 AI 同步回答
     */
    private void saveAssistantResponse(Long sessionId, AskResponse response) {
        try {
            String sourcesJson = objectMapper.writeValueAsString(response.getSourceArticles());
            sessionService.saveAssistantMessage(sessionId, response.getAnswer(), sourcesJson);
            sessionService.adjustMessageCount(sessionId);
        } catch (JsonProcessingException e) {
            log.error("AI 回答来源序列化失败", e);
            sessionService.saveAssistantMessage(sessionId, response.getAnswer(), "[]");
            sessionService.adjustMessageCount(sessionId);
        }
    }
}
