package com.im.platform.conversation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 单聊会话映射。群聊不用这张表,群聊直接用 group_id 当 chat_id。
 */
@TableName("t_conversation")
public class ConversationEntity {

    @TableId(type = IdType.INPUT)
    private Long chatId;

    private Long userA; // 恒小于 userB,应用层保证
    private Long userB;
    private Long createdAt;

    public Long getChatId() {
        return chatId;
    }

    public void setChatId(Long chatId) {
        this.chatId = chatId;
    }

    public Long getUserA() {
        return userA;
    }

    public void setUserA(Long userA) {
        this.userA = userA;
    }

    public Long getUserB() {
        return userB;
    }

    public void setUserB(Long userB) {
        this.userB = userB;
    }

    public Long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }
}
