package com.novablog.chat.service;

import com.novablog.chat.entity.ChatMessage;
import com.novablog.chat.entity.ChatSession;
import com.novablog.chat.mapper.ChatMessageMapper;
import com.novablog.chat.mapper.ChatSessionMapper;
import com.novablog.chat.vo.ChatMessageVO;
import com.novablog.chat.vo.ChatSessionDetailVO;
import com.novablog.chat.vo.ChatSessionVO;
import com.novablog.common.PageResult;
import com.novablog.common.UserContext;
import com.novablog.common.exception.BusinessException;
import com.novablog.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * AI 对话会话服务
 * 负责会话管理、消息保存、编辑与删除
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatSessionService {

    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;
    private final ChatTitleService titleService;

    /**
     * 创建新会话
     *
     * @param firstQuestion 首条用户问题，用于生成标题
     * @return 会话 VO
     */
    @Transactional
    public ChatSessionVO createSession(String firstQuestion) {
        Long userId = requireLogin();

        String title = titleService.generateTitle(firstQuestion);

        ChatSession session = new ChatSession();
        session.setUserId(userId);
        session.setTitle(title);
        session.setMessageCount(0);
        sessionMapper.insert(session);

        // 查询完整实体以获取数据库默认值
        ChatSession saved = sessionMapper.findById(session.getId());
        return toSessionVO(saved);
    }

    /**
     * 创建空会话（由前端主动创建场景）
     *
     * @param title 指定标题，为空时生成默认标题
     * @return 会话 VO
     */
    @Transactional
    public ChatSessionVO createEmptySession(String title) {
        Long userId = requireLogin();

        ChatSession session = new ChatSession();
        session.setUserId(userId);
        session.setTitle(title != null && !title.isBlank() ? title : "新对话");
        session.setMessageCount(0);
        sessionMapper.insert(session);

        // 查询完整实体以获取数据库默认值
        ChatSession saved = sessionMapper.findById(session.getId());
        return toSessionVO(saved);
    }

    /**
     * 分页查询当前用户会话列表
     */
    public PageResult<ChatSessionVO> listSessions(int page, int size) {
        Long userId = requireLogin();
        int offset = (page - 1) * size;
        List<ChatSessionVO> list = sessionMapper.findByUserId(userId, offset, size);
        Long total = sessionMapper.countByUserId(userId);
        return new PageResult<>(total, list);
    }

    /**
     * 查询会话详情（含消息）
     */
    public ChatSessionDetailVO getSessionDetail(Long sessionId) {
        Long userId = requireLogin();
        ChatSession session = sessionMapper.findById(sessionId);
        checkOwnership(session, userId);

        ChatSessionDetailVO detail = new ChatSessionDetailVO();
        detail.setId(session.getId());
        detail.setTitle(session.getTitle());
        detail.setMessageCount(session.getMessageCount());
        detail.setCreateTime(session.getCreateTime());
        detail.setUpdateTime(session.getUpdateTime());
        detail.setMessages(messageMapper.findBySessionId(sessionId));
        return detail;
    }

    /**
     * 获取会话消息列表
     */
    public List<ChatMessageVO> getSessionMessages(Long sessionId) {
        Long userId = requireLogin();
        ChatSession session = sessionMapper.findById(sessionId);
        checkOwnership(session, userId);
        return messageMapper.findBySessionId(sessionId);
    }

    /**
     * 更新会话标题
     */
    @Transactional
    public void updateTitle(Long sessionId, String title) {
        Long userId = requireLogin();
        ChatSession session = sessionMapper.findById(sessionId);
        checkOwnership(session, userId);
        sessionMapper.updateTitle(sessionId, title);
    }

    /**
     * 删除会话
     */
    @Transactional
    public void deleteSession(Long sessionId) {
        Long userId = requireLogin();
        ChatSession session = sessionMapper.findById(sessionId);
        checkOwnership(session, userId);
        sessionMapper.deleteById(sessionId);
    }

    /**
     * 保存用户问题消息
     *
     * @return 消息ID
     */
    @Transactional
    public Long saveUserMessage(Long sessionId, String content) {
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setRole("user");
        message.setContent(content);
        messageMapper.insert(message);
        return message.getId();
    }

    /**
     * 保存 AI 回答消息
     */
    @Transactional
    public void saveAssistantMessage(Long sessionId, String content, String sourcesJson) {
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setRole("assistant");
        message.setContent(content);
        message.setSourcesJson(sourcesJson);
        messageMapper.insert(message);
    }

    /**
     * 调整会话消息计数
     */
    @Transactional
    public void adjustMessageCount(Long sessionId) {
        Long count = messageMapper.countBySessionId(sessionId);
        if (count == null) {
            count = 0L;
        }
        // 直接更新为实际数量，避免增量累计出错
        ChatSession session = sessionMapper.findById(sessionId);
        if (session != null) {
            int delta = count.intValue() - session.getMessageCount();
            if (delta != 0) {
                sessionMapper.incrementMessageCount(sessionId, delta);
            }
        }
    }

    /**
     * 校验当前用户对会话的所有权
     *
     * @return 会话实体
     */
    public ChatSession validateSessionOwnership(Long sessionId) {
        Long userId = requireLogin();
        ChatSession session = sessionMapper.findById(sessionId);
        checkOwnership(session, userId);
        return session;
    }

    /**
     * 删除某条消息及其之后的所有消息
     */
    @Transactional
    public void deleteMessage(Long sessionId, Long messageId) {
        Long userId = requireLogin();
        ChatSession session = sessionMapper.findById(sessionId);
        checkOwnership(session, userId);

        ChatMessage message = messageMapper.findById(messageId);
        if (message == null || !message.getSessionId().equals(sessionId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "消息不存在");
        }

        messageMapper.deleteFromMessage(sessionId, messageId);
        adjustMessageCount(sessionId);
    }

    /**
     * 编辑用户问题并删除其后所有消息，返回历史上下文
     *
     * @return 编辑后的消息及之前的历史消息
     */
    @Transactional
    public List<ChatMessageVO> editMessage(Long sessionId, Long messageId, String newContent) {
        Long userId = requireLogin();
        ChatSession session = sessionMapper.findById(sessionId);
        checkOwnership(session, userId);

        ChatMessage message = messageMapper.findById(messageId);
        if (message == null || !message.getSessionId().equals(sessionId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "消息不存在");
        }
        if (!"user".equals(message.getRole())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "只能编辑用户问题");
        }

        // 更新问题内容
        messageMapper.updateContent(messageId, newContent);
        // 删除该消息之后的所有消息
        messageMapper.deleteFromMessage(sessionId, messageId + 1);
        // 重新校正消息计数
        adjustMessageCount(sessionId);

        // 返回编辑后的问题及之前的历史消息
        return messageMapper.findHistoryBefore(sessionId, messageId);
    }

    /**
     * 将会话实体转换为 VO
     */
    private ChatSessionVO toSessionVO(ChatSession session) {
        ChatSessionVO vo = new ChatSessionVO();
        vo.setId(session.getId());
        vo.setTitle(session.getTitle());
        vo.setMessageCount(session.getMessageCount());
        vo.setCreateTime(session.getCreateTime());
        vo.setUpdateTime(session.getUpdateTime());
        return vo;
    }

    /**
     * 要求登录并返回用户ID
     */
    private Long requireLogin() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED.getCode(), "请先登录");
        }
        return userId;
    }

    /**
     * 校验会话归属
     */
    private void checkOwnership(ChatSession session, Long userId) {
        if (session == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "会话不存在");
        }
        if (!session.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), "无权访问该会话");
        }
    }
}
