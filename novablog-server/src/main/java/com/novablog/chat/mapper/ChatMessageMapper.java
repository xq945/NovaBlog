package com.novablog.chat.mapper;

import com.novablog.chat.entity.ChatMessage;
import com.novablog.chat.vo.ChatMessageVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * AI 对话消息 Mapper
 */
@Mapper
public interface ChatMessageMapper {

    /**
     * 插入消息
     *
     * @param message 消息实体
     * @return 影响行数
     */
    int insert(ChatMessage message);

    /**
     * 根据ID查询消息
     *
     * @param id 消息ID
     * @return 消息实体
     */
    ChatMessage findById(Long id);

    /**
     * 查询会话下的所有消息
     *
     * @param sessionId 会话ID
     * @return 消息VO列表
     */
    List<ChatMessageVO> findBySessionId(Long sessionId);

    /**
     * 查询会话下某条消息之前（含）的历史消息
     *
     * @param sessionId 会话ID
     * @param messageId 消息ID
     * @return 消息VO列表
     */
    List<ChatMessageVO> findHistoryBefore(@Param("sessionId") Long sessionId,
                                              @Param("messageId") Long messageId);

    /**
     * 更新用户消息内容
     *
     * @param id      消息ID
     * @param content 新内容
     * @return 影响行数
     */
    int updateContent(@Param("id") Long id, @Param("content") String content);

    /**
     * 删除某条消息及其之后的所有消息
     *
     * @param sessionId 会话ID
     * @param messageId 消息ID
     * @return 影响行数
     */
    int deleteFromMessage(@Param("sessionId") Long sessionId, @Param("messageId") Long messageId);

    /**
     * 删除会话下的所有消息
     *
     * @param sessionId 会话ID
     * @return 影响行数
     */
    int deleteBySessionId(Long sessionId);

    /**
     * 统计会话下的消息数量
     *
     * @param sessionId 会话ID
     * @return 消息数量
     */
    Long countBySessionId(Long sessionId);
}
