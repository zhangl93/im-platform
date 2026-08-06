package com.im.platform.msg.store.mongo;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 一条消息一个文档,不做应用层分片——按 chat_id 水平扩展这件事交给 Mongo 自己的分片集群
 * (shard key + mongos)原生处理。
 * 复合索引 (chatId, messageId desc) 对应 pullHistory 的查询模式(按 chat_id 过滤 +
 * message_id 游标分页)——没有写在这里用 @CompoundIndex 声明,Spring Data MongoDB 默认
 * 不启用注解驱动的自动建索引,写了也不生效,索引由 MongoMessageIndexInitializer 在
 * 启动时显式创建一次。
 */
@Document(collection = "messages")
public class MessageDocument {

    @Id
    private Long messageId;

    private Long chatId;
    private Long senderId;
    private byte[] content;
    private Integer msgType;
    private Long serverTime;
    private String clientMsgId;
    private String ex;
    private Boolean recalled = Boolean.FALSE;

    public Long getMessageId() {
        return messageId;
    }

    public void setMessageId(Long messageId) {
        this.messageId = messageId;
    }

    public Long getChatId() {
        return chatId;
    }

    public void setChatId(Long chatId) {
        this.chatId = chatId;
    }

    public Long getSenderId() {
        return senderId;
    }

    public void setSenderId(Long senderId) {
        this.senderId = senderId;
    }

    public byte[] getContent() {
        return content;
    }

    public void setContent(byte[] content) {
        this.content = content;
    }

    public Integer getMsgType() {
        return msgType;
    }

    public void setMsgType(Integer msgType) {
        this.msgType = msgType;
    }

    public Long getServerTime() {
        return serverTime;
    }

    public void setServerTime(Long serverTime) {
        this.serverTime = serverTime;
    }

    public String getClientMsgId() {
        return clientMsgId;
    }

    public void setClientMsgId(String clientMsgId) {
        this.clientMsgId = clientMsgId;
    }

    public String getEx() {
        return ex;
    }

    public void setEx(String ex) {
        this.ex = ex;
    }

    public Boolean getRecalled() {
        return recalled;
    }

    public void setRecalled(Boolean recalled) {
        this.recalled = recalled;
    }
}
