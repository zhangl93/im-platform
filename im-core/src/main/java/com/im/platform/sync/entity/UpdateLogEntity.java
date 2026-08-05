package com.im.platform.sync.entity;

import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 按 user_id 分片的增量更新日志表(分片键 user_id,与消息表的 chat_id 分片键不同,
 * 因为查询模式是"按用户拉取全部更新"而不是"按会话")。
 */
@TableName("t_update_log")
public class UpdateLogEntity {

    private Long userId;
    private Long seq;
    private Integer eventType;
    private byte[] payload;
    private Long createdAt;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getSeq() {
        return seq;
    }

    public void setSeq(Long seq) {
        this.seq = seq;
    }

    public Integer getEventType() {
        return eventType;
    }

    public void setEventType(Integer eventType) {
        this.eventType = eventType;
    }

    public byte[] getPayload() {
        return payload;
    }

    public void setPayload(byte[] payload) {
        this.payload = payload;
    }

    public Long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }
}
