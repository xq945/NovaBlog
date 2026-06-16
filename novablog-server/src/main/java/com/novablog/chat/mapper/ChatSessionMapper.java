package com.novablog.chat.mapper;

import com.novablog.chat.entity.ChatSession;
import com.novablog.chat.vo.ChatSessionVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * AI 对话会话 Mapper
 */
@Mapper
public interface ChatSessionMapper {

    /**
     * 插入会话
     *
     * @param session 会话实体
     * @return 影响行数
     */
    int insert(ChatSession session);

    /**
     * 根据ID查询会话
     *
     * @param id 会话ID
     * @return 会话实体
     */
    ChatSession findById(Long id);

    /**
     * 分页查询用户会话列表
     *
     * @param userId 用户ID
     * @param offset 偏移量
     * @param limit  每页数量
     * @return 会话VO列表
     */
    List<ChatSessionVO> findByUserId(@Param("userId") Long userId,
                                         @Param("offset") Integer offset,
                                         @Param("limit") Integer limit);

    /**
     * 统计用户会话总数
     *
     * @param userId 用户ID
     * @return 总数
     */
    Long countByUserId(Long userId);

    /**
     * 更新会话标题
     *
     * @param id    会话ID
     * @param title 新标题
     * @return 影响行数
     */
    int updateTitle(@Param("id") Long id, @Param("title") String title);

    /**
     * 增加会话消息计数
     *
     * @param id 会话ID
     * @param delta 增加数量
     * @return 影响行数
     */
    int incrementMessageCount(@Param("id") Long id, @Param("delta") int delta);

    /**
     * 删除会话
     *
     * @param id 会话ID
     * @return 影响行数
     */
    int deleteById(Long id);
}
