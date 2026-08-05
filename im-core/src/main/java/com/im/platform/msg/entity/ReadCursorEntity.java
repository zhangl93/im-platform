package com.im.platform.msg.entity;

import com.baomidou.mybatisplus.annotation.TableName;

/** 联合主键(chat_id, user_id),读写都走 ReadCursorMapper 里的自定义 SQL,不用 BaseMapper 的 xxById 系方法。 */
@TableName("t_read_cursor")
public class ReadCursorEntity {

    private Long chatId;
    private Long userId;
    private Long readToMessageId;
    private Long updatedAt;

    public Long getChatId() {
        return chatId;
    }

    public void setChatId(Long chatId) {
        this.chatId = chatId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getReadToMessageId() {
        return readToMessageId;
    }

    public void setReadToMessageId(Long readToMessageId) {
        this.readToMessageId = readToMessageId;
    }

    public Long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Long updatedAt) {
        this.updatedAt = updatedAt;
    }
}
